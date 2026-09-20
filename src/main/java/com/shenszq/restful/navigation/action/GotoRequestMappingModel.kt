package com.shenszq.restful.navigation.action

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.gotoByName.CustomMatcherModel
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.shenszq.restful.common.UrlPatternUtils
import com.shenszq.restful.method.HttpMethod

class GotoRequestMappingModel(project: Project, contributors: Array<ChooseByNameContributor>) :
    FilteringGotoByModel<HttpMethod>(project, contributors), DumbAware, CustomMatcherModel {

    companion object {
        private const val ONLY_CURRENT_MODULE_KEY = "GoToRestService.OnlyCurrentModule"
    }

    override fun filterValueFor(item: NavigationItem): HttpMethod? =
        if (item is RestServiceItem) item.method else null

    override fun getPromptText(): String = "Enter service URL path:"
    override fun getNotInMessage(): String = IdeBundle.message("label.no.matches.found.in.project", myProject.name)
    override fun getNotFoundMessage(): String = IdeBundle.message("label.no.matches.found")
    override fun getCheckBoxMnemonic(): Char = 'N'

    override fun loadInitialCheckBoxState(): Boolean =
        PropertiesComponent.getInstance(myProject).getBoolean(ONLY_CURRENT_MODULE_KEY, false)

    override fun saveInitialCheckBoxState(state: Boolean) {
        PropertiesComponent.getInstance(myProject).setValue(ONLY_CURRENT_MODULE_KEY, state, false)
    }

    override fun getFullName(element: Any): String? = getElementName(element)
    override fun getSeparators(): Array<String> = arrayOf("/", "?")
    override fun getCheckBoxName(): String? = "当前模块"
    override fun willOpenEditor(): Boolean = true

    override fun matches(popupItem: String, userPattern: String): Boolean =
        UrlPatternUtils.matches(popupItem, userPattern)

    override fun removeModelSpecificMarkup(pattern: String): String =
        UrlPatternUtils.normalizeUserInput(pattern)
}
