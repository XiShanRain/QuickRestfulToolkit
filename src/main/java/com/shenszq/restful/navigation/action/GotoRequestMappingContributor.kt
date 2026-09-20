package com.shenszq.restful.navigation.action

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.shenszq.restful.common.ServiceHelper

class GotoRequestMappingContributor(private val module: Module?) : ChooseByNameContributor {
    @Volatile
    private var navItems = listOf<RestServiceItem>()

    override fun getNames(project: Project, onlyThisModuleChecked: Boolean): Array<String> {
        val itemList = if (onlyThisModuleChecked && module != null)
            ServiceHelper.buildRestServiceItemListUsingResolver(module)
        else ServiceHelper.buildRestServiceItemListUsingResolver(project)
        navItems = itemList
        return itemList.map { it.name ?: "" }.toTypedArray()
    }

    override fun getItemsByName(name: String, pattern: String, project: Project, onlyThisModuleChecked: Boolean): Array<NavigationItem> {
        return navItems.filter { it.name == name }.toTypedArray()
    }
}
