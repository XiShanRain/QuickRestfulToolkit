package com.shenszq.restful.common

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReferenceExpression

object PsiAnnotationHelper {
    fun getAnnotationAttributeValues(annotation: PsiAnnotation?, attr: String): List<String> {
        if (annotation == null) return emptyList()
        val value = annotation.findDeclaredAttributeValue(attr) ?: return emptyList()
        return when (value) {
            is PsiReferenceExpression -> listOf(value.text)
            is PsiLiteralExpression -> (value.value as? Any)?.toString()?.let { listOf(it) } ?: emptyList()
            is PsiArrayInitializerMemberValue -> value.initializers.map { it.text.replace("\"", "") }
            else -> emptyList()
        }
    }

    fun getAnnotationAttributeValue(annotation: PsiAnnotation?, attr: String): String? =
        getAnnotationAttributeValues(annotation, attr).firstOrNull()
}
