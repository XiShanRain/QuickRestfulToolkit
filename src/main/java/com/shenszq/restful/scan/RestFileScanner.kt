package com.shenszq.restful.scan

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.shenszq.restful.common.PsiAnnotationHelper
import com.shenszq.restful.navigation.action.RestServiceItem
import com.shenszq.restful.settings.RestfulToolkitSettings
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 文件枚举 + 文本解析器，仅用于“补漏扫描”：处理未被 PSI 索引覆盖的 .java 文件
 * （典型如未注册为 source root 的 Maven/Gradle 子模块）。
 *
 * 与索引路径互补：索引路径靠全限定名解析、快而准；这里靠注解文本简单名判断，
 * 能在无 classpath / 未索引情况下仍识别 Controller 与 Feign 接口。
 */
class RestFileScanner(private val project: Project) {

    /** es.exe 是否可用于增量查询（路径可执行 + 项目目录存在）。 */
    fun isEsUsable(): Boolean {
        val s = RestfulToolkitSettings.getInstance()
        if (s.esPath.isBlank()) return false
        val dir = project.basePath ?: return false
        val f = File(s.esPath)
        return f.exists() && f.canExecute() && dir.isNotEmpty()
    }

    /** 全量 java 文件路径（EFU 解析，仅取 .java 文件，排除同名目录）。 */
    fun allJavaPathsViaEs(): List<String> =
        runEsQuery(listOf("-ext", "java", "-efu", "-no-header"))

    /** 自 [sinceMs] 之后被修改/新建的 java 文件路径（Everything `dm:>` 增量，不扫盘）。 */
    fun changedJavaPathsViaEsSince(sinceMs: Long): List<String> {
        val dm = ES_DATE_FMT.format(Instant.ofEpochMilli(sinceMs - ES_DATE_BUFFER_MS).atZone(ZoneId.systemDefault()))
        return runEsQuery(listOf("-ext", "java", "dm:>$dm", "-efu", "-no-header"))
    }

    private fun runEsQuery(args: List<String>): List<String> {
        val s = RestfulToolkitSettings.getInstance()
        val dir = project.basePath ?: return emptyList()
        if (s.esPath.isBlank()) return emptyList()
        val cmd = listOf(s.esPath, "-path", dir) + args
        return try {
            val process = ProcessBuilder(cmd).redirectErrorStream(false).start()
            val out = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) return emptyList()
            out.lines().mapNotNull { pathFromEfu(it) }.filter { it.endsWith(".java", ignoreCase = true) }.distinct()
        } catch (e: Exception) {
            LOG.warn("Everything 查询失败: ${e.message}")
            emptyList()
        }
    }

    /** EFU 行形如 "<path>",<size>,<FILETIME>,...，取首个引号内的完整路径。 */
    private fun pathFromEfu(line: String): String? {
        val t = line.trim()
        if (t.length < 3 || t[0] != '"') return null
        val end = t.indexOf('"', 1)
        if (end < 0) return null
        return t.substring(1, end)
    }

    /** 枚举项目内全部 .java 文件：优先使用 Everything（es.exe）加速，失败回退 FilenameIndex + 文件系统递归。 */
    fun enumerateJavaFiles(): List<VirtualFile> {
        val settings = RestfulToolkitSettings.getInstance()
        val esPath = settings.esPath
        val projectDir = project.basePath
        if (esPath.isNotBlank() && projectDir != null) {
            try {
                val viaEverything = getJavaFilesViaEverything(projectDir, esPath)
                if (viaEverything.isNotEmpty()) return viaEverything
                LOG.info("Everything 未返回结果，回退 IDEA 索引 + 文件系统枚举")
            } catch (e: Exception) {
                LOG.warn("Everything 搜索失败: ${e.message}，回退 IDEA 索引 + 文件系统枚举")
            }
        }
        return getJavaFilesViaIde()
    }

    private fun getJavaFilesViaIde(): List<VirtualFile> {
        val indexed = ReadAction.compute<Collection<VirtualFile>, Throwable> {
            FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))
        }.toList()

        val projectDir = project.basePath ?: return indexed
        val fsFiles = mutableListOf<VirtualFile>()
        collectJavaFiles(File(projectDir), fsFiles)

        val merged = LinkedHashMap<String, VirtualFile>()
        (indexed + fsFiles).forEach { merged[it.path] = it }
        return merged.values.toList()
    }

    private fun collectJavaFiles(dir: File, result: MutableList<VirtualFile>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val name = child.name
            if (child.isDirectory) {
                if (name in EXCLUDED_DIRS) continue
                collectJavaFiles(child, result)
            } else if (name.endsWith(".java", ignoreCase = true)) {
                val vf = LocalFileSystem.getInstance().findFileByIoFile(child)
                    ?: LocalFileSystem.getInstance().refreshAndFindFileByIoFile(child)
                if (vf != null) result.add(vf)
            }
        }
    }

    private fun getJavaFilesViaEverything(projectDir: String, esPath: String): List<VirtualFile> {
        val esFile = File(esPath)
        if (!esFile.exists() || !esFile.canExecute()) return emptyList()

        // 复用 EFU 解析（仅 .java 文件、正确路径），再落地为 VirtualFile
        return allJavaPathsViaEs().mapNotNull { path ->
            val f = File(path)
            if (!f.isFile) return@mapNotNull null
            LocalFileSystem.getInstance().findFileByIoFile(f)
                ?: LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)
        }
    }

    /**
     * 解析单个 Java 文件，返回其中的 REST 条目（含 Feign 契约接口）。
     * 必须在 read action 上下文中调用。
     */
    fun parsePsiFile(psiFile: PsiJavaFile): List<RestServiceItem> {
        val items = mutableListOf<RestServiceItem>()
        for (clazz in psiFile.classes) {
            val isController = isControllerClass(clazz)
            val classPrefix = getClassRequestMappingPath(clazz)
            // 放行条件（等价 quick-restful 第 256 行）：Controller / 类级 @RequestMapping(Feign) / 接口
            if (!isController && classPrefix == null && !clazz.isInterface) continue

            val prefix = classPrefix ?: ""
            for (method in clazz.methods) {
                for (annotation in method.annotations) {
                    if (!annotation.isMappingAnnotationByText()) continue
                    val methodPaths = extractPaths(annotation).ifEmpty { listOf("/") }
                    val httpMethod = resolveHttpMethod(annotation)
                    for (methodPath in methodPaths) {
                        items.add(RestServiceItem(method, httpMethod, normalizePath(prefix, methodPath), null))
                    }
                }
            }
        }
        return items
    }

    // ================= 文本式注解识别（不依赖 qualifiedName 解析） =================

    private fun isControllerClass(psiClass: PsiClass): Boolean {
        if (psiClass.annotations.any { it.isControllerAnnotationByText() }) return true
        psiClass.implementsList?.referenceElements?.forEach { ref ->
            val target = ref.resolve()
            if (target is PsiClass && target.annotations.any { it.isControllerAnnotationByText() }) return true
        }
        val superClass = psiClass.superClass
        if (superClass != null && superClass.annotations.any { it.isControllerAnnotationByText() }) return true
        return false
    }

    private fun getClassRequestMappingPath(psiClass: PsiClass): String? {
        for (annotation in psiClass.annotations) {
            if (annotation.simpleNameByText() == "RequestMapping") {
                return extractPaths(annotation).firstOrNull() ?: ""
            }
        }
        return null
    }

    private fun resolveHttpMethod(annotation: PsiAnnotation): String? {
        val simple = annotation.simpleNameByText() ?: return null
        if (simple == "RequestMapping") {
            return PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "method").firstOrNull() ?: "ANY"
        }
        if (simple.endsWith("Mapping")) return simple.removeSuffix("Mapping").uppercase().ifEmpty { null }
        return null
    }

    private fun extractPaths(annotation: PsiAnnotation): List<String> =
        PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "value")
            .ifEmpty { PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "path") }
            .map { if (it.startsWith("/")) it else "/$it" }

    private fun normalizePath(classPath: String, methodPath: String): String {
        val c = classPath.trimEnd('/')
        val m = methodPath.trimStart('/')
        return "/$c/$m".replace(Regex("/+"), "/")
    }

    private fun PsiAnnotation.simpleNameByText(): String? {
        qualifiedName?.let { return it.substringAfterLast('.') }
        val text = this.text ?: return null
        return text.removePrefix("@").substringBefore("(").trim().substringAfterLast('.')
    }

    private fun PsiAnnotation.isMappingAnnotationByText(): Boolean {
        val simple = simpleNameByText() ?: return false
        return simple.endsWith("Mapping")
    }

    private fun PsiAnnotation.isControllerAnnotationByText(): Boolean =
        simpleNameByText() in setOf("Controller", "RestController", "ControllerAdvice")

    private companion object {
        val LOG = Logger.getInstance(RestFileScanner::class.java)
        val ES_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        const val ES_DATE_BUFFER_MS = 120_000L
        val EXCLUDED_DIRS = setOf(
            ".git", ".idea", ".svn", ".hg",
            "target", "build", "out", "node_modules", "dist", "classes", "generated"
        )
    }
}
