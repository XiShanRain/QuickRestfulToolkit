package com.shenszq.restful.navigation.action

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.shenszq.restful.common.ToolkitIcons
import com.shenszq.restful.method.HttpMethod
import javax.swing.Icon

class RestServiceItem(
    private val psiElement: PsiElement,
    requestMethod: String?,
    private val url: String,
    private val module: Module?
) : NavigationItem {
    val method: HttpMethod? = HttpMethod.getByRequestMethod(requestMethod)
    private val locationString: String? = ReadAction.compute<String?, RuntimeException> { buildLocationString(psiElement, module?.name) }
    val dedupKey: String = ReadAction.compute<String, RuntimeException> { buildDedupKey(psiElement, url, method) }

    /** 该条目所在文件的绝对路径；用于计算“索引已覆盖文件集”，供补漏扫描做差集。 */
    val filePath: String? = ReadAction.compute<String?, RuntimeException> {
        psiElement.containingFile?.virtualFile?.path
    }

    override fun getName(): String? = url
    override fun getPresentation(): ItemPresentation? = RestServiceItemPresentation()

    override fun navigate(requestFocus: Boolean) {
        val navigatable: Navigatable? = ReadAction.compute<Navigatable?, RuntimeException> {
            val navElement = psiElement.navigationElement
            if (navElement is Navigatable) navElement else null
        }
        if (navigatable != null && navigatable.canNavigate()) navigatable.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val navElement = psiElement.navigationElement
        if (navElement is Navigatable) navElement.canNavigate() else false
    }

    override fun canNavigateToSource(): Boolean = canNavigate()

    private inner class RestServiceItemPresentation : ItemPresentation {
        override fun getPresentableText(): String? = url
        // 必须用 this@RestServiceItem 显式限定：ItemPresentation 是 Java 接口，其 getLocationString()
        // 会被 Kotlin 暴露为合成属性 locationString，直接写 locationString 会解析成本方法导致无限递归。
        override fun getLocationString(): String? = this@RestServiceItem.locationString
        override fun getIcon(unused: Boolean): Icon? = ToolkitIcons.Method.get(method)
    }

    companion object {
        private fun buildDedupKey(psiElement: PsiElement, url: String, method: HttpMethod?): String {
            val navElement = psiElement.navigationElement
            val base = navElement ?: psiElement
            val file = base.containingFile
            val virtualFile = file?.virtualFile
            val fileKey = when {
                virtualFile != null -> virtualFile.path
                file != null -> file.name
                else -> base.javaClass.name
            }
            return "$url|$method|$fileKey|${base.textOffset}"
        }

        private fun buildLocationString(psiElement: PsiElement, moduleName: String?): String? {
            if (psiElement is PsiMethod) {
                val containingClass = psiElement.containingClass
                if (containingClass != null) {
                    val modPart = moduleName?.let { "$it " } ?: ""
                    return "($modPart${containingClass.name}#${psiElement.name})"
                }
            }
            return psiElement.containingFile?.let { "(${it.name})" }
        }
    }
}
