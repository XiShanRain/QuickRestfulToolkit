package com.shenszq.restful.navigation.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.shenszq.restful.navigation.ui.RestSearchUiSwitch

/**
 * 全局切换“首选搜索 UI”：对话框（quick-restful 式）与 ChooseByName 弹层互为兜底。
 * 翻转偏好后**立即**以新偏好重开对应窗口，切换即时可见（不再是“下次生效”）。
 */
class SwitchRestSearchUiAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val dialogPreferred = RestSearchUiSwitch.flipPreferenceOnly()
        val next = if (dialogPreferred) "对话框搜索" else "弹层搜索（兜底）"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("RestfulRadar")
            .createNotification(
                "RestfulRadar: 首选入口已切换为$next。",
                NotificationType.INFORMATION
            )
            .notify(project)
        // 立即用新偏好重开，让“切换”有即时可见效果。
        val action = ActionManager.getInstance().getAction("Toolkit.GotoService") as? GotoRequestMappingAction
            ?: return
        if (dialogPreferred) action.showAsDialog(project) else action.showAsPopup(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
