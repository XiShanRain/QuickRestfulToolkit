package com.shenszq.restful.navigation.action

import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.shenszq.restful.common.UrlPatternUtils

class RestServiceChooseByNamePopup private constructor(
    project: Project?,
    model: ChooseByNameModel,
    provider: ChooseByNameItemProvider,
    oldPopup: ChooseByNamePopup?,
    predefinedText: String?,
    mayRequestOpenInCurrentWindow: Boolean,
    initialIndex: Int
) : ChooseByNamePopup(project, model, provider, oldPopup, predefinedText, mayRequestOpenInCurrentWindow, initialIndex) {

    companion object {
        val CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY = Key<RestServiceChooseByNamePopup>("ChooseByNamePopup")

        @JvmStatic
        fun createPopup(
            project: Project?,
            model: ChooseByNameModel,
            provider: ChooseByNameItemProvider,
            predefinedText: String?,
            mayRequestOpenInCurrentWindow: Boolean,
            initialIndex: Int
        ): RestServiceChooseByNamePopup {
            val normalizedText = if (StringUtil.isEmptyOrSpaces(predefinedText)) predefinedText
            else UrlPatternUtils.normalizeUserInput(predefinedText)
            if (!StringUtil.isEmptyOrSpaces(normalizedText)) {
                return RestServiceChooseByNamePopup(project, model, provider, null, normalizedText, mayRequestOpenInCurrentWindow, initialIndex)
            }
            val oldPopup = project?.getUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY)
            oldPopup?.close(false)
            val newPopup = RestServiceChooseByNamePopup(project, model, provider, oldPopup, normalizedText, mayRequestOpenInCurrentWindow, initialIndex)
            project?.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup)
            return newPopup
        }
    }

    override fun transformPattern(pattern: String): String = UrlPatternUtils.normalizeUserInput(pattern)
}
