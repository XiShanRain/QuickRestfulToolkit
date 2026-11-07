package com.shenszq.restful.navigation.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.shenszq.restful.navigation.action.GotoRequestMappingAction
import com.shenszq.restful.settings.RestfulToolkitSettings

/**
 * 在“对话框搜索（首选）”与“ChooseByName 弹层搜索（兜底）”之间切换。
 *
 * 切换 = 翻转 [RestfulToolkitSettings.dialogSearchPreferred] + 关闭当前窗口 +
 * 以翻转后的偏好重新触发主入口动作（仅带 PROJECT 上下文，走全项目范围）。
 */
object RestSearchUiSwitch {

    /** 从对话框切回弹层。 */
    fun toggleToPopup(project: Project, dialog: RestSearchDialog?) {
        RestfulToolkitSettings.getInstance().dialogSearchPreferred = false
        // 切换不是“确认”，用 Cancel 码关闭，避免被当成 OK 结果处理
        dialog?.close(DialogWrapper.CANCEL_EXIT_CODE)
        reopen(project, asPopup = true)
    }

    /** 从弹层切到对话框。 */
    fun toggleToDialog(project: Project, popupToClose: (() -> Unit)?) {
        RestfulToolkitSettings.getInstance().dialogSearchPreferred = true
        popupToClose?.invoke()
        reopen(project, asPopup = false)
    }

    /** 全局动作仅翻转偏好，无需重开：下次 Ctrl+Alt+/ 生效。 */
    fun flipPreferenceOnly(): Boolean {
        val settings = RestfulToolkitSettings.getInstance()
        settings.dialogSearchPreferred = !settings.dialogSearchPreferred
        return settings.dialogSearchPreferred
    }

    private fun reopen(project: Project, asPopup: Boolean) {
        // 延后到当前窗口关闭完成之后再重新打开另一个 UI。
        // 用 nonModal() 而不是 any()：any() 的回调可能被 FlushQueue 拉进写意图执行栈等
        // 不安全上下文里立即执行（此时上一个模态窗口尚未完成关闭），nonModal() 保证在
        // “无模态窗口”的正常事件循环时机执行，等价于“上一个窗口确实关完之后”。
        ApplicationManager.getApplication().invokeLater({
            val action = ActionManager.getInstance().getAction("Toolkit.GotoService") as? GotoRequestMappingAction
                ?: return@invokeLater
            if (asPopup) action.showAsPopup(project) else action.showAsDialog(project)
        }, ModalityState.nonModal())
    }
}
