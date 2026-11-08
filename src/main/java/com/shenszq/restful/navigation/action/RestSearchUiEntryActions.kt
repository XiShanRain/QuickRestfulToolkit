package com.shenszq.restful.navigation.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * 独立入口：始终以“对话框式搜索”打开 REST 路径检索（全项目范围），
 * 不受 [com.shenszq.restful.settings.RestfulToolkitSettings.dialogSearchPreferred] 偏好影响。
 *
 * 与 [OpenRestSearchPopupAction] 成对提供：两者是各自独立的动作，可在 IDE Keymap 中
 * 分别绑定快捷键，互不冲突，从而把“对话框”和“弹层”两种入口固定到不同的键位上。
 * 复用 [GotoRequestMappingAction] 已有的 [GotoRequestMappingAction.showAsDialog]（该路径不经过
 * actionPerformed，直接用 DialogWrapper 展示，无需任何跨动作共享状态）。
 */
class OpenRestSearchDialogAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        gotoAction()?.showAsDialog(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * 独立入口：始终以“ChooseByName 弹层搜索”打开 REST 路径检索（全项目范围），
 * 不受首选 UI 偏好影响。可在 IDE Keymap 中单独绑定快捷键，与对话框入口互不影响。
 * 复用 [GotoRequestMappingAction.showAsPopup]（内部直接用 ChooseByNamePopup.invoke 展示，不依赖共享静态标记）。
 */
class OpenRestSearchPopupAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        gotoAction()?.showAsPopup(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/** 通过 ActionManager 取主入口动作实例，委托其对话框/弹层展示方法。 */
private fun gotoAction(): GotoRequestMappingAction? =
    ActionManager.getInstance().getAction("Toolkit.GotoService") as? GotoRequestMappingAction
