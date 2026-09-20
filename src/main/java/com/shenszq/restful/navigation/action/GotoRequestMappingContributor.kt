package com.shenszq.restful.navigation.action

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.shenszq.restful.scan.RestScanService

class GotoRequestMappingContributor(private val module: Module?) : ChooseByNameContributor {
    @Volatile
    private var navItems = listOf<RestServiceItem>()

    override fun getNames(project: Project, onlyThisModuleChecked: Boolean): Array<String> {
        val itemList = RestScanService.getInstance(project).collectItems(onlyThisModuleChecked, module)
        navItems = itemList
        return itemList.map { it.name ?: "" }.toTypedArray()
    }

    override fun getItemsByName(name: String, pattern: String, project: Project, onlyThisModuleChecked: Boolean): Array<NavigationItem> {
        return navItems.filter { it.name == name }.toTypedArray()
    }
}
