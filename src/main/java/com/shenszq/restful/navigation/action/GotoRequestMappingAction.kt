package com.shenszq.restful.navigation.action

import com.intellij.ide.util.gotoByName.ChooseByNameFilter
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.shenszq.restful.method.HttpMethod
import com.shenszq.restful.navigation.ui.RestSearchDialog
import com.shenszq.restful.navigation.ui.RestServicesToolWindowFactory
import com.shenszq.restful.settings.RestfulToolkitSettings
import java.awt.datatransfer.DataFlavor

/**
 * 主搜索入口（默认快捷键 Ctrl+Alt+/）：按设置里的「默认窗口」打开对应窗口 ——
 * 「模态对话框（全项目搜索）」或「右侧 RestfulRadar 面板（按模块浏览）」。
 *
 * - 默认（交替关闭）：每次按下都打开默认窗口；
 * - 开启“每次按下交替”后：先翻转默认窗口再按翻转结果打开，连续按会在两者间轮流。
 * IDE 自带的 ChooseByName 浮层仍可由 `Go to REST Service (Popup)`
 * （[OpenRestSearchPopupAction]）单独绑定快捷键使用。
 *
 * 重要重构（A 方案）：本动作**不再继承** `GotoActionBase`。后者用一个跨所有 goto 动作
 * 共享的 `protected static myInAction` 做防重入，只要它非空，`GotoActionBase.update()` 就会把
 * 本动作置为 disabled，导致“第一次能用、之后热键静默失效（无日志无异常）”——此前靠反射手工
 * 清该标记，非常脆弱。现在：
 * - 对话框直接用 `DialogWrapper`（[RestSearchDialog]）打开；
 * - 弹层直接用 `ChooseByNamePopup.invoke(callback, ModalityState.current(), true)` 打开，
 *   方法过滤 UI 由构造 [ChooseByNameFilter] 时内部 `setToolArea` 完成。
 * 全程不触碰那个全局静态标记，从根本上消除该类问题。
 */
class GotoRequestMappingAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val useDialog = resolveUiForInvoke()
        LOG.info("[RestfulRadar] actionPerformed: project=${project.name}, ui=${if (useDialog) "dialog" else "toolWindow"}")
        if (project.isDisposed) return
        if (useDialog) {
            openDialog(project, tryFindCopiedUrl())
        } else {
            openToolWindow(project)
        }
    }

    /**
     * 决定本次按下用哪个 UI，并把“下一次要用的 UI”写回设置：
     * - 开启交替（默认）：翻转偏好，本次即按翻转后的结果打开；
     * - 关闭交替：保持偏好不变，固定用同一种 UI。
     */
    private fun resolveUiForInvoke(): Boolean {
        val settings = RestfulToolkitSettings.getInstance()
        if (!settings.alternateSearchUiOnInvoke) return settings.dialogSearchPreferred
        val next = !settings.dialogSearchPreferred
        settings.dialogSearchPreferred = next
        return next
    }

    override fun update(e: AnActionEvent) {
        // 仅在有项目时可用；不再受任何跨动作静态标记影响。
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /** 供切换动作 / 独立入口 / 工具窗口调用：以对话框打开，全项目范围。 */
    fun showAsDialog(project: Project) = openDialog(project, tryFindCopiedUrl())

    /** 供切换动作 / 独立入口 / 工具窗口调用：以弹层打开，全项目范围。 */
    fun showAsPopup(project: Project) = openPopup(project, null, tryFindCopiedUrl())

    private fun openDialog(project: Project, initialText: String?) {
        if (project.isDisposed) return
        LOG.info("[RestfulRadar] openDialog: create + show")
        val dialog = RestSearchDialog(project, initialText)
        // 同步进入模态循环（平台动作的标准做法）。不要改用 invokeLater(ModalityState.any()) 推迟：
        // 该回调会被 FlushQueue 拉进“已有写意图执行栈”中运行，showAndGet 的模态事件泵整体嵌套在
        // 写意图栈内；对话框打开期间应用激活等事件触发 VFS 刷新时，平台报
        // “Write-unsafe context! ... (not "any")” SEVERE 并归咎本插件。
        val confirmed = dialog.showAndGet()
        LOG.info("[RestfulRadar] openDialog: modal loop exited, confirmed=$confirmed, target=${dialog.chosenItem?.url}")
        // 导航必须在模态循环完全退出之后执行：不要在对话框 doOKAction 内 close() 后立即导航。
        if (confirmed) dialog.chosenItem?.navigate(true)
    }

    private fun openToolWindow(project: Project) {
        if (project.isDisposed) return
        LOG.info("[RestfulRadar] openToolWindow: activate right panel + focus filter")
        RestServicesToolWindowFactory.activateAndFocusFilter(project)
    }

    private fun openPopup(project: Project, module: Module?, predefinedText: String?) {
        if (project.isDisposed) return
        LOG.info("[RestfulRadar] openPopup: create + invoke")
        val contributors: Array<ChooseByNameContributor> = arrayOf(GotoRequestMappingContributor(module))
        val model = GotoRequestMappingModel(project, contributors)
        val provider = GotoRequestMappingProvider(null)
        val popup = RestServiceChooseByNamePopup.createPopup(project, model, provider, predefinedText, false, 0)
        // 构造即通过 ChooseByNameFilter 内部 setToolArea 把“HTTP 方法过滤”UI 安装到弹层。
        val filter = GotoRequestMappingFilter(popup, model, project)
        popup.invoke(object : ChooseByNamePopupComponent.Callback() {
            override fun elementChosen(element: Any) {
                if (element is RestServiceItem && element.canNavigate()) element.navigate(true)
            }

            override fun onClose() {
                filter.close()
            }
        }, ModalityState.current(), true)
    }

    private fun tryFindCopiedUrl(): String? {
        // 必须显式指定泛型实参 <String>：否则 Kotlin 无法从上下文推断 getContents 的 T，
        // 会在返回处插入到错误类型的强转，运行期抛 ClassCastException(String -> Void)。
        val content = runCatching {
            CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        }.getOrNull() ?: return null
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

    companion object {
        private val LOG = Logger.getInstance(GotoRequestMappingAction::class.java)
    }
}
