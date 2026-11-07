package com.shenszq.restful.navigation.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.shenszq.restful.navigation.ui.RestSearchUiSwitch

/**
 * 全局切换“首选搜索 UI”：对话框（quick-restful 式）与 ChooseByName 弹层互为兜底。
 * 仅翻转偏好设置，下次 Ctrl+Alt+/ 生效；窗口内的 Ctrl+Alt+P 可即时切换并重开。
 */
class SwitchRestSearchUiAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val dialogPreferred = RestSearchUiSwitch.flipPreferenceOnly()
        val next = if (dialogPreferred) "对话框搜索" else "弹层搜索（兜底）"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("RestfulRadar")
            .createNotification(
                "RestfulRadar: 首选入口已切换为$next，下次 Ctrl+Alt+/ 生效。",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
