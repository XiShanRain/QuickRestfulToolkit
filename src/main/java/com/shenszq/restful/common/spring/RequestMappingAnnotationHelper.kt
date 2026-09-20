package com.shenszq.restful.common.spring

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.shenszq.restful.annotations.SpringRequestMethodAnnotation
import com.shenszq.restful.common.PsiAnnotationHelper
import com.shenszq.restful.method.RequestPath

object RequestMappingAnnotationHelper {
    fun getRequestPaths(psiClass: PsiClass): List<RequestPath> {
        val annotation = findMappingAnnotation(psiClass.annotations)
        if (annotation != null) {
            val result = getRequestMappings(annotation, "/")
            if (result.isNotEmpty()) return result
        }
        val superClass = psiClass.superClass
        if (superClass != null && superClass.qualifiedName != "java.lang.Object") {
            return getRequestPaths(superClass)
        }
        return listOf(RequestPath("/", null))
    }

    fun getRequestPaths(psiMethod: PsiMethod): List<RequestPath> {
        val list = mutableListOf<RequestPath>()
        for (annotation in psiMethod.annotations) {
            val qualName = annotation.qualifiedName ?: continue
            if (SpringRequestMethodAnnotation.getByQualifiedName(qualName) == null) continue
            list.addAll(getRequestMappings(annotation, "/"))
        }
        return list
    }

    private fun getRequestMappings(annotation: PsiAnnotation, defaultValue: String): List<RequestPath> {
        val qualName = annotation.qualifiedName ?: return emptyList()
        val reqAnnotation = SpringRequestMethodAnnotation.getByQualifiedName(qualName) ?: return emptyList()
        val declaredMethod = reqAnnotation.methodName
        val methodList = declaredMethod?.let { listOf(it) }
            ?: PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "method")
        val pathList = PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "value").toMutableList()
        if (pathList.isEmpty()) pathList.addAll(PsiAnnotationHelper.getAnnotationAttributeValues(annotation, "path"))
        if (pathList.isEmpty()) pathList.add(defaultValue)
        if (methodList.isEmpty()) return pathList.map { RequestPath(it, null) }
        return methodList.flatMap { method -> pathList.map { RequestPath(it, method) } }
    }

    private fun findMappingAnnotation(annotations: Array<PsiAnnotation>): PsiAnnotation? =
        annotations.firstOrNull { annotation ->
            val qualName = annotation.qualifiedName ?: return@firstOrNull false
            SpringRequestMethodAnnotation.getByQualifiedName(qualName) != null
        }
}
