package com.shenszq.restful.common.jaxrs

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.shenszq.restful.annotations.JaxrsHttpMethodAnnotation
import com.shenszq.restful.annotations.JaxrsPathAnnotation
import com.shenszq.restful.common.PsiAnnotationHelper
import com.shenszq.restful.method.RequestPath

object JaxrsAnnotationHelper {
    fun getRequestPaths(psiMethod: PsiMethod): List<RequestPath> {
        val path = getPathValue(findPathAnnotation(psiMethod.annotations))
        val resolvedPath = path?.trim() ?: psiMethod.name
        return psiMethod.annotations.mapNotNull { annotation ->
            val qualName = annotation.qualifiedName ?: return@mapNotNull null
            JaxrsHttpMethodAnnotation.getByQualifiedName(qualName)
                ?.let { RequestPath(resolvedPath, it.methodName) }
        }
    }

    fun getClassUriPath(psiClass: PsiClass): String = getPathValue(findPathAnnotation(psiClass.annotations)) ?: ""

    private fun findPathAnnotation(annotations: Array<PsiAnnotation>): PsiAnnotation? =
        annotations.firstOrNull { annotation ->
            val qualName = annotation.qualifiedName ?: return@firstOrNull false
            JaxrsPathAnnotation.entries.any { it.qualifiedName == qualName }
        }

    private fun getPathValue(annotation: PsiAnnotation?): String? =
        PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "value")
}
