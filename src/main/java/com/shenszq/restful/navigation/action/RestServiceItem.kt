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
        override fun getLocationString(): String? = locationString
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
