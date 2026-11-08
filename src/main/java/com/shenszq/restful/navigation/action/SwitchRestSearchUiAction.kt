package com.xishanrain.restful.navigation.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.xishanrain.restful.navigation.ui.RestSearchUiSwitch
import com.xishanrain.restful.navigation.ui.RestServicesToolWindowFactory

/**
 * 全局切换“首选搜索形态”：模态对话框与右侧 RestfulRadar 工具窗口互为两种形态。
 * 翻转偏好后**立即**以新偏好重开对应窗口，切换即时可见（不再是“下次生效”）。
 */
class SwitchRestSearchUiAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val dialogPreferred = RestSearchUiSwitch.flipPreferenceOnly()
        val next = if (dialogPreferred) "对话框搜索" else "右侧 RestfulRadar 面板"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("RestfulRadar")
            .createNotification(
                "RestfulRadar: 首选入口已切换为$next。",
                NotificationType.INFORMATION
            )
            .notify(project)
        // 立即用新偏好重开，让“切换”有即时可见效果。
        val action = ActionManager.getInstance().getAction(RestfulRadarActions.GOTO_SERVICE) as? GotoRequestMappingAction
            ?: return
        if (dialogPreferred) action.showAsDialog(project)
        else RestServicesToolWindowFactory.activateAndFocusFilter(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
