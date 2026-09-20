package com.shenszq.restful.common.resolver

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.shenszq.restful.method.RequestPath
import com.shenszq.restful.navigation.action.RestServiceItem

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
        return RestServiceItem(psiElement, requestPath.method, "$classPath$methodPath", myModule)
    }
}
