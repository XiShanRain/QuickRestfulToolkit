package com.shenszq.restful.scan

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.shenszq.restful.common.ServiceHelper
import com.shenszq.restful.navigation.action.RestServiceItem
import com.shenszq.restful.settings.RestfulToolkitSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 统一搜索入口：索引为主 + 差集补漏 + 合并去重，并做项目级结果缓存。
 *
 * 性能策略（对齐 quick-restful 的“扫一次即复用 + Everything 增量”）：
 * - 项目级整批结果缓存：源码未变化时打开直接命中缓存，瞬间出结果；
 * - VFS 监听失效驱动：任意 .java/.kt 变化即置脏，下次打开增量重建一次；
 * - 补漏库存式增量：首次全量扫描入库存（path -> items + mtime）；之后仅用 Everything
 *   `dm:>` 取“上次之后改动的少量文件”重解析，避免每次枚举/refresh 全部 .java；
 * - Everything 不可用或关闭缓存时，回退为全量枚举（仅在脏时发生）。
 */
@Service(Service.Level.PROJECT)
class RestScanService(private val project: Project) : Disposable {

    private val fileScanner = RestFileScanner(project)

    private val lock = Any()
    private val warming = AtomicBoolean(false)

    /** 补漏库存：path -> 该文件产出的 REST 条目（空列表表示已确认无映射）。 */
    private val gapItemsByPath = ConcurrentHashMap<String, List<RestServiceItem>>()

    /** 补漏库存：path -> 上次解析时的 mtime，用于失效判断。 */
    private val gapMtimeByPath = ConcurrentHashMap<String, Long>()

    /** 上次补漏扫描完成时刻（ms）；为 0 表示需要从全量重来。 */
    @Volatile
    private var lastGapScanMs = 0L

    @Volatile
    private var dirty = true

    @Volatile
    private var cachedItems: List<RestServiceItem>? = null

    @Volatile
    private var cachedSignature: String? = null

    /** 缓存代次；任何失效/重建都会自增，用于丢弃过期的后台预热结果。 */
    @Volatile
    private var generation = 0L

    /** 最近一次重建的概况（模式/条目数/耗时），仅供对话框状态行展示。 */
    @Volatile
    var lastRebuildInfo: String = ""
        private set

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, SourceChangeListener())
    }

    fun collectItems(onlyThisModule: Boolean, module: Module?): List<RestServiceItem> {
        // 模块范围使用较少，直接按模块作用域计算，不进项目缓存
        if (onlyThisModule && module != null) {
            val items = ServiceHelper.buildRestServiceItemListUsingResolver(module)
            return items.distinctBy { it.dedupKey }
        }

        var snapshot = cachedItems
        if (dirty || snapshot == null || currentSignature() != cachedSignature) {
            synchronized(lock) {
                if (dirty || cachedItems == null || currentSignature() != cachedSignature) {
                    // 设置项变化时，补漏库存整体重来
                    if (currentSignature() != cachedSignature) resetGapState()
                    snapshot = buildAll()
                    cachedItems = snapshot
                    cachedSignature = currentSignature()
                    dirty = false
                    generation++ // 使在途的后台预热结果作废
                } else {
                    snapshot = cachedItems
                }
            }
        }
        return snapshot ?: emptyList()
    }

    /**
     * 项目启动后在后台预热缓存：非阻塞读操作（自动等待索引就绪、可取消）。
     * 统一走 [collectItems]（内部持锁 + 写缓存），避免与弹窗侧的同步构建并发跑
     * buildAll 而争用 gapItemsByPath 等非线程安全库存。
     */
    fun warmUp() {
        if (!dirty && cachedItems != null) return
        if (!warming.compareAndSet(false, true)) return
        ReadAction.nonBlocking<Unit> { collectItems(false, null) }
            .expireWhen { project.isDisposed }
            .finishOnUiThread(ModalityState.any()) { warming.set(false) }
            .submit(ForkJoinPool.commonPool())
    }

    /** 强制下次打开重新扫描（增量重建，不丢弃补漏库存）。 */
    fun invalidate() {
        dirty = true
        generation++
    }

    /** 彻底清空所有缓存（含补漏库存），下次打开做全量重建。 */
    fun clearCaches() {
        resetGapState()
        cachedItems = null
        dirty = true
        generation++
    }

    private fun resetGapState() {
        gapItemsByPath.clear()
        gapMtimeByPath.clear()
        lastGapScanMs = 0L
    }

    private fun buildAll(): List<RestServiceItem> {
        val startedAt = System.currentTimeMillis()
        val settings = RestfulToolkitSettings.getInstance()

        val indexItems = ServiceHelper.buildRestServiceItemListUsingResolver(project, settings.methodLevelScanEnabled)

        if (!settings.gapFillEnabled) {
            val items = indexItems.distinctBy { it.dedupKey }
            lastRebuildInfo = "index-only · ${items.size} items · ${System.currentTimeMillis() - startedAt} ms"
            LOG.info("[QuickRestfulToolkit] rebuild(index-only) items=${items.size}")
            return items
        }

        val covered = indexItems.mapNotNull { it.filePath }.toHashSet()
        val usedDelta = rebuildGap(covered, settings)
        val gapItems = ArrayList<RestServiceItem>()
        gapItemsByPath.forEach { (path, items) -> if (path !in covered) gapItems.addAll(items) }

        val items = (indexItems + gapItems).distinctBy { it.dedupKey }
        lastRebuildInfo = "${if (usedDelta) "delta" else "full"} · ${items.size} items · ${System.currentTimeMillis() - startedAt} ms"
        LOG.info(
            "[QuickRestfulToolkit] rebuild index=${indexItems.size} gapFiles=${gapItemsByPath.size} " +
                "gapItems=${gapItems.size} mode=${if (usedDelta) "delta" else "full"} coveredFiles=${covered.size}"
        )
        return items
    }

    /** 重建补漏库存；返回本次是否走了 Everything 增量（用于日志）。 */
    private fun rebuildGap(covered: Set<String>, settings: RestfulToolkitSettings): Boolean {
        val startedAt = System.currentTimeMillis()
        val canDelta = settings.useCache && lastGapScanMs > 0L && fileScanner.isEsUsable()

        if (canDelta) {
            deltaGapScan(covered)
        } else {
            fullGapScan(covered)
        }
        // 记录本轮开始时刻；查询侧再自行向前留缓冲，避免边界漏扫（重复解析幂等）
        lastGapScanMs = startedAt
        return canDelta
    }

    /** 首次 / 关闭缓存 / 无 Everything：全量枚举并解析所有未覆盖文件。 */
    private fun fullGapScan(covered: Set<String>) {
        gapItemsByPath.clear()
        gapMtimeByPath.clear()
        for (vf in fileScanner.enumerateJavaFiles()) {
            ProgressManager.checkCanceled()
            val path = vf.path
            if (path in covered) continue
            gapItemsByPath[path] = parseItems(vf)
            gapMtimeByPath[path] = vf.timeStamp
        }
    }

    /** Everything 增量：只重解析自上次以来改动/新建的文件，并剔除已删除的文件。 */
    private fun deltaGapScan(covered: Set<String>) {
        val changed = fileScanner.changedJavaPathsViaEsSince(lastGapScanMs)
        for (path in changed) {
            ProgressManager.checkCanceled()
            val io = File(path)
            if (!io.exists()) {
                gapItemsByPath.remove(path); gapMtimeByPath.remove(path); continue
            }
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(io) ?: continue
            if (vf.path in covered) {
                gapItemsByPath.remove(vf.path); gapMtimeByPath.remove(vf.path); continue
            }
            gapItemsByPath[vf.path] = parseItems(vf)
            gapMtimeByPath[vf.path] = vf.timeStamp
        }
        // 删除的文件不会出现在 dm: 结果里，需对库存路径做一次存在性核对（数量很小）
        for (tracked in gapItemsByPath.keys.toList()) {
            if (!File(tracked).exists()) {
                gapItemsByPath.remove(tracked); gapMtimeByPath.remove(tracked)
            }
        }
    }

    private fun parseItems(vf: VirtualFile): List<RestServiceItem> {
        if (!vf.isValid) return emptyList()
        val psiFile = ReadAction.compute<PsiFile?, Throwable> { PsiManager.getInstance(project).findFile(vf) }
        if (psiFile !is PsiJavaFile) return emptyList()
        return ReadAction.compute<List<RestServiceItem>, Throwable> { fileScanner.parsePsiFile(psiFile) }
    }

    /** 设置项变化时让缓存签名失效，触发下次重建。 */
    private fun currentSignature(): String {
        val s = RestfulToolkitSettings.getInstance()
        return "${s.gapFillEnabled}|${s.methodLevelScanEnabled}|${s.useCache}|${s.esPath}"
    }

    override fun dispose() {
        // messageBus.connect(this) 会随本服务一起释放，无需手动断开
    }

    private inner class SourceChangeListener : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            val base = project.basePath ?: return
            val relevant = events.any { event ->
                val path = event.path
                path.startsWith(base) &&
                    (path.endsWith(".java", ignoreCase = true) || path.endsWith(".kt", ignoreCase = true))
            }
            if (relevant && !dirty) {
                invalidate()
                LOG.debug("[QuickRestfulToolkit] source changed, cache invalidated")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(RestScanService::class.java)
        fun getInstance(project: Project): RestScanService = project.service()
    }
}
