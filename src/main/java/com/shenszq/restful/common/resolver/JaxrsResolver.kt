package com.shenszq.restful.common.resolver

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.shenszq.restful.annotations.JaxrsPathAnnotation
import com.shenszq.restful.common.jaxrs.JaxrsAnnotationHelper
import com.shenszq.restful.navigation.action.RestServiceItem

class JaxrsResolver(private val module: Module?) : BaseServiceResolver() {
    constructor(project: Project) : this(null) { myProject = project }

    override fun getRestServiceItemList(project: Project, scope: GlobalSearchScope): List<RestServiceItem> {
        val items = mutableListOf<RestServiceItem>()
        val visitedClasses = linkedSetOf<String>()
        for (pathAnnotation in JaxrsPathAnnotation.entries) {
            val annotationClass = JavaPsiFacade.getInstance(project)
                .findClass(pathAnnotation.qualifiedName, GlobalSearchScope.allScope(project)) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { psiClass ->
                val key = psiClass.qualifiedName
                    ?: "${psiClass.containingFile.virtualFile}:${psiClass.textOffset}"
                if (visitedClasses.add(key)) items.addAll(getServiceItemList(psiClass))
            }
        }
        return items
    }

    private fun getServiceItemList(psiClass: PsiClass): List<RestServiceItem> {
        val classUriPath = JaxrsAnnotationHelper.getClassUriPath(psiClass)
        val items = mutableListOf<RestServiceItem>()
        for (psiMethod in psiClass.methods) {
            val methodRequestPaths = JaxrsAnnotationHelper.getRequestPaths(psiMethod)
            if (methodRequestPaths.isEmpty()) continue
            for (methodRequestPath in methodRequestPaths) {
                items.add(createRestServiceItem(psiMethod, classUriPath, methodRequestPath))
            }
        }
        return items
    }
}
