package com.shenszq.restful.navigation.action

import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.shenszq.restful.common.UrlPatternUtils
import com.shenszq.restful.navigation.ui.RestSearchUiSwitch
import com.shenszq.restful.settings.RestfulToolkitSettings
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class RestServiceChooseByNamePopup private constructor(
    project: Project?,
    model: ChooseByNameModel,
    provider: ChooseByNameItemProvider,
    oldPopup: ChooseByNamePopup?,
    predefinedText: String?,
    mayRequestOpenInCurrentWindow: Boolean,
    initialIndex: Int
) : ChooseByNamePopup(project, model, provider, oldPopup, predefinedText, mayRequestOpenInCurrentWindow, initialIndex) {

    init {
        // 兜底弹层内注册 Ctrl+Alt+P：切回对话框首选（getTextField 在父类构造后就绪，仍做防御）。
        getTextField()?.registerKeyboardAction(
            { getProject()?.let { p -> RestSearchUiSwitch.toggleToDialog(p) { close(true) } } },
            KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK or KeyEvent.ALT_DOWN_MASK),
            JComponent.WHEN_FOCUSED
        )
    }

    /** 弹层也尊重“打开时一直显示列表”设置（IDE 框架原生支持空查询展示）。 */
    override fun isShowListForEmptyPattern(): Boolean = RestfulToolkitSettings.getInstance().alwaysShowResultList

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
