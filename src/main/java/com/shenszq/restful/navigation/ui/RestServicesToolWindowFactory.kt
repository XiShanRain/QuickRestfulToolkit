package com.shenszq.restful.navigation.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.shenszq.restful.common.ToolkitIcons
import com.shenszq.restful.common.UrlPatternUtils
import com.shenszq.restful.navigation.action.GotoRequestMappingAction
import com.shenszq.restful.navigation.action.RestServiceItem
import com.shenszq.restful.navigation.action.RestfulRadarActions
import com.shenszq.restful.scan.RestScanService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.ForkJoinPool
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * 右侧工具窗口（对齐原版 RestServices 窗口）：
 * - 工具栏：刷新（重扫）+ 搜索（打开全项目搜索，UI 跟随设置）+ URL 过滤框；
 * - 树：根"共找到 N 个服务" → 模块 → 服务（图标区分方法，文本为 URL）；仅保留搜索过滤，
 *   其余交互与原版一致：单击服务填充下方请求面板、双击/回车跳转源码；
 * - 服务节点右键：复制完整 URL / 跳转到源码；
 * - 底部：请求调试面板（method + URL + Send + RequestParams/RequestBody/Response 页签）。
 *
 * 数据统一来自 [RestScanService]（与搜索对话框共用同一份项目级缓存），窗口每次显示自动刷新。
 */
class RestServicesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RestServicesPanel(project)
        project.putUserData(REST_PANEL_KEY, panel)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        /** 须与 plugin.xml 中 `<toolWindow id="...">` 保持一致。 */
        const val TOOL_WINDOW_ID = "RestfulRadar"

        private val REST_PANEL_KEY: Key<RestServicesPanel> = Key.create("RestfulRadar.RestServicesPanel")

        /**
         * 弹出/展开右侧 RestfulRadar 工具窗口，并把焦点放到 URL 过滤框。
         * 供主入口 Ctrl+Alt+/ 的“面板形态”使用。窗口不存在时返回 false。
         */
        fun activateAndFocusFilter(project: Project): Boolean {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return false
            toolWindow.activate(null, true)
            // 内容可能刚被懒加载创建，延后一拍再抢焦点（对话框/编辑器焦点切换也在此时进行）。
            ApplicationManager.getApplication().invokeLater({
                project.getUserData(REST_PANEL_KEY)?.focusFilterField()
            }, ModalityState.nonModal())
            return true
        }
    }
}

private class RestServicesPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val filterField = JBTextField()
    private val treeRoot = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = Tree(treeModel)
    private val detailPanel = RestServiceDetailPanel(project)

    private var allItems: List<RestServiceItem> = emptyList()

    /** 后台重载代次：旧任务返回时若代次已变则丢弃结果，避免乱序覆盖新结果。 */
    private var reloadToken = 0

    init {
        add(createNorthPanel(), BorderLayout.NORTH)
        add(createSplitter(), BorderLayout.CENTER)

        // 窗口每次显示时刷新一次（collectItems 未失效时直接返回缓存）
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) = reload()
        })

        reload()
    }

    /** 聚焦 URL 过滤框并全选，便于直接输入替换旧关键字（由主入口 Ctrl+Alt+/ 调用）。 */
    fun focusFilterField() {
        IdeFocusManager.getInstance(project).requestFocus(filterField, true)
        filterField.selectAll()
    }

    private fun createNorthPanel(): JComponent {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction("刷新", "重新扫描 REST 服务", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = refresh()
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction("搜索", "在模态对话框中全项目搜索 REST 服务", AllIcons.Actions.Search) {
            override fun actionPerformed(e: AnActionEvent) = openSearch()
        })
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, actionGroup, true)
        toolbar.setTargetComponent(this)

        filterField.toolTipText = "按 URL 过滤：支持 /user/{id} 模板与后缀匹配；留空显示全部"
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })
        filterField.registerKeyboardAction({ refresh() },
            KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_FOCUSED)
        val filterWrapper = JBPanel<JBPanel<*>>(BorderLayout())
        filterWrapper.border = JBUI.Borders.empty(2, 6, 2, 2)
        filterWrapper.add(filterField, BorderLayout.CENTER)

        val northPanel = JBPanel<JBPanel<*>>(BorderLayout())
        northPanel.add(toolbar.getComponent(), BorderLayout.WEST)
        northPanel.add(filterWrapper, BorderLayout.CENTER)
        return northPanel
    }

    private fun createSplitter(): JComponent {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.rowHeight = 20
        tree.cellRenderer = RestNodeRenderer()
        tree.addTreeSelectionListener { handleSelection() }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && !e.isPopupTrigger) navigateSelected()
            }

            override fun mousePressed(e: MouseEvent) {
                handlePopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                handlePopup(e)
            }

            private fun handlePopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                tree.selectionPath = path
                if (selectedItem() != null) contextMenu().show(tree, e.x, e.y)
            }
        })
        tree.registerKeyboardAction({ navigateSelected() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
        tree.registerKeyboardAction({ refresh() },
            KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_FOCUSED)

        val splitter = JBSplitter(true, 0.5f)
        splitter.firstComponent = JBScrollPane(tree)
        splitter.secondComponent = detailPanel
        return splitter
    }

    /** 服务节点右键菜单（对齐原版 Toolkit.NavigatorServiceMenu：复制完整 URL / 跳转到源码）。 */
    private fun contextMenu(): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(JMenuItem("复制完整 URL").apply { addActionListener { copyFullUrl() } })
        menu.add(JMenuItem("跳转到源码").apply { addActionListener { navigateSelected() } })
        return menu
    }

    /**
     * 打开全项目搜索对话框。本面板自己已有 URL 过滤框，因此这里固定弹对话框（不再“打开自己”）。
     */
    private fun openSearch() {
        val action = ActionManager.getInstance().getAction(RestfulRadarActions.GOTO_SERVICE) as? GotoRequestMappingAction
            ?: return
        action.showAsDialog(project)
    }

    /** 刷新：清缓存后重扫（配置文件缓存一并失效）。 */
    private fun refresh() {
        RestScanService.getInstance(project).clearCaches()
        RestHostResolver.invalidate()
        reload()
    }

    private fun reload() {
        if (project.isDisposed) return
        val token = ++reloadToken
        val service = RestScanService.getInstance(project)
        ReadAction.nonBlocking<List<RestServiceItem>> { service.collectItems(false, null) }
            .expireWhen { project.isDisposed }
            // 本面板是工具窗口组件：回调只更新自身 Swing 组件，用 ANY 避免被模态队列挂起。
            .finishOnUiThread(ModalityState.any()) { list ->
                if (token != reloadToken) return@finishOnUiThread
                allItems = list
                applyFilter()
            }
            .submit(ForkJoinPool.commonPool())
    }

    private fun applyFilter() {
        val query = filterField.text
        val normalized = UrlPatternUtils.normalizeUserInput(query)
        val isEmptyQuery = StringUtil.isEmptyOrSpaces(query) || normalized == "/" || normalized.isEmpty()
        val filtered = if (isEmptyQuery) allItems else allItems.filter { UrlPatternUtils.matches(it.url, normalized) }
        rebuildTree(filtered)
    }

    /** 树结构（对齐原版）：根（服务总数）→ 模块 → 服务（URL）。 */
    private fun rebuildTree(items: List<RestServiceItem>) {
        val root = DefaultMutableTreeNode(RootNode(items.size))
        items.groupBy { it.moduleName ?: "未归属模块" }
            .toSortedMap()
            .forEach { (moduleName, moduleItems) ->
                val moduleNode = DefaultMutableTreeNode(ModuleNode(moduleName))
                moduleItems.sortedBy { it.url }.forEach { moduleNode.add(DefaultMutableTreeNode(it)) }
                root.add(moduleNode)
            }
        treeModel.setRoot(root)
        tree.expandRow(0)
    }

    private fun handleSelection() {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
        when (val userObject = node.userObject) {
            is RestServiceItem -> fillDetail(userObject)
            is RootNode, is ModuleNode -> resetDetail()
            else -> Unit
        }
    }

    /** 单击服务节点：填充详情（对齐原版 showServiceDetail）。 */
    private fun fillDetail(item: RestServiceItem) {
        val fallbackUrl = RestHostResolver.DEFAULT_URI + item.url
        val values = runCatching {
            ReadAction.compute<DetailValues, RuntimeException> {
                val fullUrl = RestHostResolver.serviceHostPrefix(item.module) + item.url
                val method = RestMethodRequestInfo.toPsiMethod(item.psiElement)
                val params = method?.let { RestMethodRequestInfo.buildParamString(it) }.orEmpty()
                val body = method?.let { RestMethodRequestInfo.buildRequestBodyJson(it) }.orEmpty()
                DetailValues(fullUrl, params, body)
            }
        }.getOrElse { DetailValues(fallbackUrl, "", "") }

        detailPanel.resetRequestTabbedPane()
        detailPanel.setMethodValue(item.method?.name ?: "GET")
        detailPanel.setUrlValue(values.fullUrl)
        detailPanel.addRequestParamsTab(values.params)
        if (values.body.isNotBlank()) detailPanel.addRequestBodyTabPanel(values.body)
    }

    /** 根/模块节点：重置详情（对齐原版 resetRestServiceDetail）。 */
    private fun resetDetail() {
        detailPanel.resetRequestTabbedPane()
        detailPanel.setMethodValue("GET")
        detailPanel.setUrlValue("URL")
        detailPanel.initTab()
    }

    private fun selectedItem(): RestServiceItem? =
        (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? RestServiceItem

    /** 双击/回车：跳转到源码；条目失效时先刷新树。 */
    private fun navigateSelected() {
        val item = selectedItem() ?: return
        if (!item.canNavigate()) {
            reload()
            return
        }
        item.navigate(true)
    }

    private fun copyFullUrl() {
        val item = selectedItem() ?: return
        val fullUrl = RestHostResolver.serviceHostPrefix(item.module) + item.url
        CopyPasteManager.getInstance().setContents(StringSelection(fullUrl))
    }

    private data class DetailValues(val fullUrl: String, val params: String, val body: String)

    private class RootNode(val count: Int)
    private class ModuleNode(val name: String)

    /** 树渲染：根（图标 + 服务总数）/ 模块 / 服务（方法图标 + URL）。 */
    private class RestNodeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
                is RootNode -> {
                    icon = AllIcons.Nodes.Module
                    append("共找到 ${node.count} 个服务", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is ModuleNode -> {
                    icon = AllIcons.Nodes.Module
                    append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                is RestServiceItem -> {
                    icon = ToolkitIcons.Method.get(node.method)
                    append(node.url, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                else -> Unit
            }
        }

        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            // 限制单行最大宽度，避免超长 URL 把树撑出过宽的横向滚动条
            size.width = size.width.coerceAtMost(900)
            return size
        }
    }
}
