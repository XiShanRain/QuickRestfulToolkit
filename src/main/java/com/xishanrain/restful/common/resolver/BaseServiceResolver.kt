package com.xishanrain.restful.common.resolver

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.xishanrain.restful.method.RequestPath
import com.xishanrain.restful.navigation.action.RestServiceItem

abstract class BaseServiceResolver : ServiceResolver {
    protected var myModule: Module? = null
    protected var myProject: Project? = null

    override fun findAllSupportedServiceItemsInModule(): List<RestServiceItem> {
        val module = myModule ?: return emptyList()
        return getRestServiceItemList(module.project, GlobalSearchScope.moduleScope(module))
    }

    override fun findAllSupportedServiceItemsInProject(): List<RestServiceItem> {
        val project = myProject ?: myModule?.project ?: return emptyList()
        return getRestServiceItemList(project, GlobalSearchScope.projectScope(project))
    }

    protected abstract fun getRestServiceItemList(project: Project, scope: GlobalSearchScope): List<RestServiceItem>

    protected fun createRestServiceItem(psiElement: PsiElement, classUriPath: String, requestPath: RequestPath): RestServiceItem {
        var classPath = classUriPath
        if (!classPath.startsWith("/")) classPath = "/$classPath"
        if (!classPath.endsWith("/")) classPath = "$classPath/"
        var methodPath = requestPath.path
        if (methodPath.startsWith("/")) methodPath = methodPath.substring(1)
        // 项目级扫描时 myModule 为 null：按元素所在文件反查其 IDEA Module，
        // 让工具窗口能正确按模块分组（否则全部落入“未归属模块”）。
        val itemModule = myModule ?: ModuleUtil.findModuleForPsiElement(psiElement)
        return RestServiceItem(psiElement, requestPath.method, "$classPath$methodPath", itemModule)
    }
}
