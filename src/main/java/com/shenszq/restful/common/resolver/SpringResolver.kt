package com.shenszq.restful.common.resolver

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.shenszq.restful.annotations.SpringControllerAnnotation
import com.shenszq.restful.common.spring.RequestMappingAnnotationHelper
import com.shenszq.restful.method.RequestPath
import com.shenszq.restful.navigation.action.RestServiceItem

class SpringResolver(private val module: Module?) : BaseServiceResolver() {
    constructor(project: Project) : this(null) { myProject = project }

    override fun getRestServiceItemList(project: Project, scope: GlobalSearchScope): List<RestServiceItem> {
        val items = mutableListOf<RestServiceItem>()
        val visitedClasses = linkedSetOf<String>()
        for (controllerAnnotation in SpringControllerAnnotation.entries) {
            val annotationClass = JavaPsiFacade.getInstance(project)
                .findClass(controllerAnnotation.qualifiedName, GlobalSearchScope.allScope(project)) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { psiClass ->
                val key = psiClass.qualifiedName
                    ?: "${psiClass.containingFile.virtualFile}:${psiClass.textOffset}"
                if (visitedClasses.add(key)) items.addAll(getServiceItemList(psiClass))
            }
        }
        return items
    }

    private fun getServiceItemList(psiClass: PsiClass): List<RestServiceItem> {
        val classRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(psiClass)
        val items = mutableListOf<RestServiceItem>()
        for (psiMethod in psiClass.methods) {
            val methodRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(psiMethod)
            if (methodRequestPaths.isEmpty()) continue
            for (classRequestPath in classRequestPaths) {
                for (methodRequestPath in methodRequestPaths) {
                    items.add(createRestServiceItem(psiMethod, classRequestPath.path, methodRequestPath))
                }
            }
        }
        return items
    }
}
