package com.shenszq.restful.common

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.shenszq.restful.common.resolver.JaxrsResolver
import com.shenszq.restful.common.resolver.SpringResolver
import com.shenszq.restful.navigation.action.RestServiceItem

object ServiceHelper {
    fun buildRestServiceItemListUsingResolver(module: Module): List<RestServiceItem> = distinctItems(
        listOf(
            SpringResolver(module).findAllSupportedServiceItemsInModule(),
            JaxrsResolver(module).findAllSupportedServiceItemsInModule()
        )
    )

    fun buildRestServiceItemListUsingResolver(project: Project): List<RestServiceItem> = distinctItems(
        listOf(
            SpringResolver(project).findAllSupportedServiceItemsInProject(),
            JaxrsResolver(project).findAllSupportedServiceItemsInProject()
        )
    )

    private fun distinctItems(batches: List<List<RestServiceItem>>): List<RestServiceItem> {
        val uniqueItems = linkedMapOf<String, RestServiceItem>()
        for (batch in batches) for (item in batch) uniqueItems.putIfAbsent(item.dedupKey, item)
        return uniqueItems.values.toList()
    }
}
