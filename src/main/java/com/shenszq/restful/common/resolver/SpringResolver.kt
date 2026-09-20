package com.shenszq.restful.common.resolver

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.shenszq.restful.annotations.SpringControllerAnnotation
import com.shenszq.restful.annotations.SpringRequestMethodAnnotation
import com.shenszq.restful.common.spring.RequestMappingAnnotationHelper
import com.shenszq.restful.navigation.action.RestServiceItem

/**
 * Spring 接口解析器（索引驱动，纯内存查找，不逐文件解析）。
 *
 * 召回入口：
 * 1. 类级注解 @Controller / @RestController —— 常规控制器；
 * 2. 类级注解 @RequestMapping —— 覆盖 Feign 契约接口（无 Controller 注解、类上有路径前缀）
 *    以及只标 @RequestMapping 的控制器；
 * 3.（可选）方法级 mapping 注解反查所在类 —— 兜住“类上无注解、仅方法带 mapping”的极端接口。
 */
class SpringResolver(private val module: Module?, private val methodLevelScan: Boolean = false) : BaseServiceResolver() {
    constructor(project: Project, methodLevelScan: Boolean = false) : this(null, methodLevelScan) { myProject = project }

    override fun getRestServiceItemList(project: Project, scope: GlobalSearchScope): List<RestServiceItem> {
        val candidateClasses = linkedMapOf<String, PsiClass>()
        val allScope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)

        // 入口 1 & 2：类级注解命中的类（Controller / RestController / RequestMapping）
        for (qualifiedName in classLevelSearchAnnotations()) {
            val annotationClass = facade.findClass(qualifiedName, allScope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { psiClass ->
                registerClass(candidateClasses, psiClass)
            }
        }

        // 入口 3：方法级 mapping 注解反查所在类（可选，默认关）
        if (methodLevelScan) {
            for (req in SpringRequestMethodAnnotation.entries) {
                val annotationClass = facade.findClass(req.qualifiedName, allScope) ?: continue
                AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope).forEach { psiMethod ->
                    psiMethod.containingClass?.let { registerClass(candidateClasses, it) }
                }
            }
        }

        val items = mutableListOf<RestServiceItem>()
        for (psiClass in candidateClasses.values) items.addAll(getServiceItemList(psiClass))
        return items
    }

    private fun classLevelSearchAnnotations(): List<String> = buildList {
        addAll(SpringControllerAnnotation.entries.map { it.qualifiedName })
        // 类级 @RequestMapping：覆盖 Feign 契约接口
        add(SpringRequestMethodAnnotation.REQUEST_MAPPING.qualifiedName)
    }

    private fun registerClass(candidateClasses: MutableMap<String, PsiClass>, psiClass: PsiClass) {
        val key = psiClass.qualifiedName
            ?: "${psiClass.containingFile.virtualFile}:${psiClass.textOffset}"
        candidateClasses.putIfAbsent(key, psiClass)
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
