package com.shenszq.restful.navigation.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.shenszq.restful.scan.RestScanService

/**
 * 显式“重新扫描”：丢弃整批结果缓存与补漏库存，下次打开/后台重建会走全量扫描。
 * 用于 Everything/索引未能感知到的极端场景（如大批外部改动）作为兜底入口。
 */
class RescanRestAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        RestScanService.getInstance(project).let { service ->
            service.clearCaches()
            service.warmUp()
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("QuickRestfulToolkit")
            .createNotification(
                "QuickRestfulToolkit: 已清空缓存，正在后台重新全量扫描 REST 映射。",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
