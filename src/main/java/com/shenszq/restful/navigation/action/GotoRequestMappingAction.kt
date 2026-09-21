package com.shenszq.restful.navigation.action

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.util.gotoByName.ChooseByNameFilter
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.shenszq.restful.method.HttpMethod
import com.shenszq.restful.navigation.ui.RestSearchDialog
import com.shenszq.restful.settings.RestfulToolkitSettings
import java.awt.datatransfer.DataFlavor

class GotoRequestMappingAction : GotoActionBase(), DumbAware {

    override fun gotoActionPerformed(e: AnActionEvent) {
        val project = e.project
        val dialogPreferred = RestfulToolkitSettings.getInstance().dialogSearchPreferred
        // 入口日志：排查“热键无反应”时用于区分“动作未被调用”与“动作被调用但窗口未生效”。
        LOG.info("[QuickRestfulToolkit] gotoActionPerformed: project=${project?.name}, preferred=${if (dialogPreferred) "dialog" else "popup"}")
        if (project == null) return
        if (dialogPreferred) {
            openDialog(project, tryFindCopiedUrl())
            return
        }
        openPopup(
            project,
            LangDataKeys.MODULE.getData(e.dataContext),
            getPsiContext(e),
            tryFindCopiedUrl()
        )
    }

    /** 供切换动作调用：以对话框（首选 UI）打开，全项目范围。 */
    fun showAsDialog(project: Project) = openDialog(project, tryFindCopiedUrl())

    /** 供切换动作调用：以 ChooseByName 弹层（兜底 UI）打开，全项目范围。 */
    fun showAsPopup(project: Project) = openPopup(project, null, null, tryFindCopiedUrl())

    private fun openDialog(project: Project, initialText: String?) {
        if (project.isDisposed) {
            // 极端情况下（动作执行瞬间项目被销毁）：同样要清掉 actionPerformed 已设置的防重入标记，
            // 否则标记残留会让热键此后永久失效（见下方 finally 注释）。
            clearMyInActionIfMine()
            return
        }
        LOG.info("[QuickRestfulToolkit] openDialog: create + show")
        val dialog = RestSearchDialog(project, initialText)
        val confirmed = try {
            // 同步进入模态循环（平台动作的标准做法）。不要改用 invokeLater(ModalityState.any()) 推迟：
            // 该回调会被 FlushQueue 拉进“已有写意图执行栈”中运行，showAndGet 的模态事件泵整体嵌套在
            // 写意图栈内；对话框打开期间应用激活等事件触发 VFS 刷新时，平台报
            // “Write-unsafe context! ... (not "any")” SEVERE 并归咎本插件。
            dialog.showAndGet()
        } finally {
            // 根因修复（“第一次弹窗正常、之后热键再按完全无反应”）：GotoActionBase 用
            // protected static myInAction 做防重入，actionPerformed 时置为本动作类，但只在
            // ChooseByName 弹层的关闭回调（GotoActionBase$1.onClose）中清除；对话框分支不经过该
            // 回调，标记永久残留 → 此后 update() 恒 disabled → 2024.3 快捷键执行链
            // （ActionManagerImplKt.tryToExecuteNow）在 isEnabled 检查处静默跳过（无日志无异常）。
            clearMyInActionIfMine()
        }
        LOG.info("[QuickRestfulToolkit] openDialog: modal loop exited, confirmed=$confirmed, target=${dialog.chosenItem?.url}")
        // 导航必须在模态循环完全退出之后执行：不要在对话框 doOKAction 内 close() 后立即导航。
        if (confirmed) dialog.chosenItem?.navigate(true)
    }

    private fun openPopup(project: Project, module: Module?, context: PsiElement?, predefinedText: String?) {
        // 对话框→弹层的“切换重开”路径不经过 actionPerformed：此时防重入标记为空，而
        // showNavigationPopup 入口会 assertTrue(myInAction != null)（不满足会抛断言错），
        // 且弹层关闭回调只清除与其入口快照相等的标记。故缺失时补设，关闭时由平台自行清除。
        ensureMyInActionMark()
        try {
            val contributors: Array<ChooseByNameContributor> = arrayOf(GotoRequestMappingContributor(module))
            val model = GotoRequestMappingModel(project, contributors)

            val callback = object : GotoActionCallback<HttpMethod>() {
                override fun createFilter(popup: ChooseByNamePopup): ChooseByNameFilter<HttpMethod> =
                    GotoRequestMappingFilter(popup, model, project)

                override fun elementChosen(popup: ChooseByNamePopup, element: Any) {
                    if (element is RestServiceItem && element.canNavigate()) element.navigate(true)
                }
            }

            val provider = GotoRequestMappingProvider(context)
            showNavigationPopup(
                callback, "Request Mapping URL matching pattern",
                RestServiceChooseByNamePopup.createPopup(project, model, provider, predefinedText, false, 0),
                true
            )
        } catch (t: Throwable) {
            // 弹层未能打开时清掉标记，避免残留导致热键永久失效（尤其是“切换重开”路径，
            // 它不在 actionPerformed 的 catch(Throwable) 兜底范围内）。
            clearMyInActionIfMine()
            throw t
        }
    }

    private fun tryFindCopiedUrl(): String? {
        // 必须显式指定泛型实参 <String>：否则 Kotlin 无法从上下文推断 getContents 的 T，
        // 会在返回处插入到错误类型的强转，运行期抛 ClassCastException(String -> Void)。
        val content = runCatching {
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        }.getOrNull() ?: return null
        val trimmed = content.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return if (trimmed.length <= 240) trimmed else trimmed.substring(0, 240)
        }
        return null
    }

    private class GotoRequestMappingFilter(
        popup: ChooseByNamePopup,
        model: GotoRequestMappingModel,
        project: Project
    ) : ChooseByNameFilter<HttpMethod>(popup, model, GotoRequestMappingConfiguration.getInstance(project), project) {
        override fun getAllFilterValues(): List<HttpMethod> = HttpMethod.entries
        override fun textForFilterValue(value: HttpMethod): String = value.name
        override fun iconForFilterValue(value: HttpMethod) = null
    }

    /**
     * 补设 [GotoActionBase] 的防重入标记（protected static Class myInAction）为本动作类，仅在其为空时。
     * 该字段为 Java protected static，Kotlin 直接引用会被编译拒绝，故用反射访问；
     * runCatching 兜底保证未来平台若移除/重命名字段时退化为原有行为而不是崩溃。
     */
    private fun ensureMyInActionMark() {
        val field = MY_IN_ACTION_FIELD ?: return
        runCatching {
            if (field.get(null) == null) {
                field.set(null, javaClass)
            }
        }
    }

    /** 清除非空且属于本动作类的防重入标记（对话框分支缺少平台 onClose 清理路径，见 openDialog）。 */
    private fun clearMyInActionIfMine() {
        val field = MY_IN_ACTION_FIELD ?: return
        runCatching {
            if (field.get(null) == javaClass) {
                field.set(null, null)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(GotoRequestMappingAction::class.java)

        /** GotoActionBase 的防重入标记字段（protected static Class myInAction），详见 openDialog 注释。 */
        private val MY_IN_ACTION_FIELD: java.lang.reflect.Field? = runCatching {
            GotoActionBase::class.java.getDeclaredField("myInAction").apply { isAccessible = true }
        }.getOrNull()
    }
}
