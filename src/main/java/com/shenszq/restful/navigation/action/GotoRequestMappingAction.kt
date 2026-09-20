package com.shenszq.restful.navigation.action

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.util.gotoByName.ChooseByNameFilter
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.shenszq.restful.method.HttpMethod
import java.awt.datatransfer.DataFlavor

class GotoRequestMappingAction : GotoActionBase(), DumbAware {
    override fun gotoActionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val contributors: Array<ChooseByNameContributor> = arrayOf(GotoRequestMappingContributor(LangDataKeys.MODULE.getData(e.dataContext)))
        val model = GotoRequestMappingModel(project, contributors)

        val callback = object : GotoActionCallback<HttpMethod>() {
            override fun createFilter(popup: ChooseByNamePopup): ChooseByNameFilter<HttpMethod> =
                GotoRequestMappingFilter(popup, model, project)

            override fun elementChosen(popup: ChooseByNamePopup, element: Any) {
                if (element is RestServiceItem && element.canNavigate()) element.navigate(true)
            }
        }

        val provider = GotoRequestMappingProvider(getPsiContext(e))
        showRestServiceNavigationPopup(
            e, model, callback,
            "Request Mapping URL matching pattern",
            true, true, provider
        )
    }

    protected fun showRestServiceNavigationPopup(
        e: AnActionEvent, model: ChooseByNameModel,
        callback: GotoActionCallback<HttpMethod>,
        findUsagesTitle: String?,
        useSelectionFromEditor: Boolean,
        allowMultipleSelection: Boolean,
        itemProvider: ChooseByNameItemProvider
    ) {
        val project = e.getData(CommonDataKeys.PROJECT)
        val start = getInitialText(useSelectionFromEditor, e)
        val predefinedText = if (start.first == null) tryFindCopiedUrl() else start.first
        showNavigationPopup(
            callback, findUsagesTitle,
            RestServiceChooseByNamePopup.createPopup(
                project, model, itemProvider, predefinedText, false, start.second
            ),
            allowMultipleSelection
        )
    }

    private fun tryFindCopiedUrl(): String? {
        val content = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) ?: return null
        val trimmed = content.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return if (trimmed.length <= 240) trimmed else trimmed.substring(0, 240)
        }
        return null
    }

    private class GotoRequestMappingFilter(
        popup: ChooseByNamePopup,
        model: GotoRequestMappingModel,
        project: Project
    ) : ChooseByNameFilter<HttpMethod>(popup, model, GotoRequestMappingConfiguration.getInstance(project), project) {
        override fun getAllFilterValues(): List<HttpMethod> = HttpMethod.entries
        override fun textForFilterValue(value: HttpMethod): String = value.name
        override fun iconForFilterValue(value: HttpMethod) = null
    }
}
