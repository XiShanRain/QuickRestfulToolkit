package com.shenszq.restful.navigation.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.shenszq.restful.common.ToolkitIcons
import com.shenszq.restful.common.UrlPatternUtils
import com.shenszq.restful.method.HttpMethod
import com.shenszq.restful.navigation.action.RestServiceItem
import com.shenszq.restful.scan.RestScanService
import com.shenszq.restful.settings.RestfulToolkitSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyEvent
import java.util.concurrent.ForkJoinPool
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 对话框式 REST 路径搜索（迁移自 quick-restful 的交互形态，作为首选入口）。
 *
 * 与 quick-restful 原版差异：不自建扫描/缓存，数据统一来自 [RestScanService]
 * （项目级缓存 + Everything 增量），仅迁移“输入框 + 全量列表 + 扫描按钮 + 快捷键”交互。
 * 性能策略：输入防抖（[FILTER_DEBOUNCE_MS] ms）+ 加载时预计算匹配键（[UrlPatternUtils.EndpointKey]）
 * + 可显示结果数上限（[RestfulToolkitSettings.maxVisibleResults]），避免每键全量重算/重绘卡 EDT。
 * - 增量刷新（F5）：[RestScanService.invalidate] 后后台重建，走 Everything dm: 增量；
 * - 全量重新扫描（Ctrl+Enter）：[RestScanService.clearCaches] 后全量重建；
 * - 空查询是否展示全量由 [RestfulToolkitSettings.alwaysShowResultList] 决定；
 * - Ctrl+Alt+P / “弹层搜索”按钮切回 ChooseByName 弹层兜底。
 */
class RestSearchDialog(
    private val project: Project,
    initialText: String?
) : DialogWrapper(project) {

    private val textField = JBTextField()
    private val listModel = CollectionListModel<RestServiceItem>()
    private val resultList = JBList(listModel)
    private val statusLabel = JLabel()
    private val methodCombo = javax.swing.JComboBox<HttpMethod?>()

    /** 预计算匹配行（按 url 预排序）；每轮按键过滤直接复用它，免重复归一化/编译正则/排序。 */
    private var searchRows: List<SearchRow> = emptyList()
    private var loading = false
    private var scanInfo: String = ""
    private var totalItems: Int = 0

    /** 输入防抖：停手约 120ms 后才真正过滤，避免每个字符都全量重算 + 重绘。 */
    private val filterTimer = Timer(FILTER_DEBOUNCE_MS) { applyFilter(textField.text) }.apply {
        isRepeats = false
    }

    @Volatile
    private var dismissed = false

    /** 用户确认选中的结果；导航由调用方在 showAndGet() 返回（模态循环退出）后执行。 */
    var chosenItem: RestServiceItem? = null
        private set

    private val incrementalButton = JButton("增量刷新")
    private val fullScanButton = JButton("全量重新扫描")
    private val popupButton = JButton("弹层搜索")

    init {
        title = "搜索 REST 路径"
        init()

        // 方法过滤下拉：null 表示“全部方法”
        methodCombo.addItem(null)
        HttpMethod.entries.forEach { methodCombo.addItem(it) }
        methodCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<out Any?>, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = if (value == null) "全部方法" else value.toString()
                return this
            }
        }
        methodCombo.addActionListener { applyFilter(textField.text) }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = scheduleFilter()
            override fun removeUpdate(e: DocumentEvent?) = scheduleFilter()
            override fun changedUpdate(e: DocumentEvent?) = scheduleFilter()
        })

        registerShortcuts()

        // 后台加载初始数据（缓存命中则瞬间返回）
        rebuild(RebuildMode.NONE)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(640, 420)

        // 顶部：输入框 + 方法过滤
        val topPanel = JPanel(BorderLayout(6, 0))
        textField.preferredSize = Dimension(460, 30)
        topPanel.add(textField, BorderLayout.CENTER)
        methodCombo.preferredSize = Dimension(120, 30)
        topPanel.add(methodCombo, BorderLayout.EAST)
        panel.add(topPanel, BorderLayout.NORTH)

        // 中部：结果列表
        resultList.cellRenderer = RestItemRenderer()
        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) doOKAction()
            }
        })
        val scrollPane = JBScrollPane(resultList)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 底部：扫描控制 + 切换 + 状态行
        val bottomPanel = JPanel()
        bottomPanel.layout = javax.swing.BoxLayout(bottomPanel, javax.swing.BoxLayout.Y_AXIS)

        val controlRow = JPanel(BorderLayout())
        val switchLabel = JLabel("  ")
        controlRow.add(switchLabel, BorderLayout.WEST)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        incrementalButton.toolTipText = "走 Everything 增量重建（F5）"
        incrementalButton.addActionListener { rebuild(RebuildMode.INCREMENTAL) }
        fullScanButton.toolTipText = "清空缓存并全量扫描（Ctrl+Enter）"
        fullScanButton.addActionListener { rebuild(RebuildMode.FULL) }
        popupButton.toolTipText = "切换到 ChooseByName 弹层搜索（Ctrl+Alt+P）"
        popupButton.addActionListener { RestSearchUiSwitch.toggleToPopup(project, this) }
        rightPanel.add(incrementalButton)
        rightPanel.add(fullScanButton)
        rightPanel.add(popupButton)
        controlRow.add(rightPanel, BorderLayout.EAST)

        statusLabel.foreground = java.awt.Color.GRAY
        statusLabel.font = statusLabel.font.deriveFont(statusLabel.font.size2D - 1f)
        val statusRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        statusRow.add(statusLabel)

        bottomPanel.add(controlRow)
        bottomPanel.add(statusRow)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = textField

    override fun dispose() {
        dismissed = true
        super.dispose()
    }

    override fun doOKAction() {
        // 只记录选择并正常关闭。不要再在这里 close()+navigate()：那会在模态事件泵内部关闭
        // 窗口并导航/抢焦点，破坏后续键盘事件到动作系统的路由，导致再次按热键静默失效。
        val item = resultList.selectedValue
            ?: if (listModel.size > 0) listModel.getElementAt(0) else null
            ?: run {
                statusLabel.text = "没有可导航的结果"
                return
            }
        if (!item.canNavigate()) {
            statusLabel.text = "该结果无法导航，可能源码已变化，请增量/全量刷新"
            return
        }
        chosenItem = item
        super.doOKAction()
    }

    private fun registerShortcuts() {
        val root = rootPane
        root.registerKeyboardAction({ rebuild(RebuildMode.INCREMENTAL) },
            KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        root.registerKeyboardAction({ rebuild(RebuildMode.FULL) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW)
        root.registerKeyboardAction({ RestSearchUiSwitch.toggleToPopup(project, this) },
            KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK or KeyEvent.ALT_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW)
        // 输入框 → 列表（首/末项）
        textField.registerKeyboardAction(
            { focusList(first = true) },
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_FOCUSED)
        textField.registerKeyboardAction(
            { focusList(first = false) },
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_FOCUSED)
    }

    private fun focusList(first: Boolean) {
        if (listModel.size == 0) return
        resultList.requestFocusInWindow()
        resultList.selectedIndex = if (first) 0 else listModel.size - 1
    }

    private enum class RebuildMode { NONE, INCREMENTAL, FULL }

    private fun rebuild(mode: RebuildMode) {
        if (loading || dismissed) return
        loading = true
        setControlsEnabled(false)
        statusLabel.text = when (mode) {
            RebuildMode.INCREMENTAL -> "增量刷新中…"
            RebuildMode.FULL -> "全量扫描中…"
            RebuildMode.NONE -> "加载中…"
        }
        val service = RestScanService.getInstance(project)
        when (mode) {
            RebuildMode.INCREMENTAL -> service.invalidate()
            RebuildMode.FULL -> service.clearCaches()
            RebuildMode.NONE -> Unit
        }
        ReadAction.nonBlocking<List<RestServiceItem>> { service.collectItems(false, null) }
            .expireWhen { project.isDisposed || dismissed }
            // 本对话框是模态窗口：回调只更新自身组件，用 ANY 避免被模态队列挂起到窗口关闭；
            // dismissed 标志保证窗口已关时不再触碰已销毁组件。
            .finishOnUiThread(ModalityState.any()) { list ->
                if (dismissed) return@finishOnUiThread
                loading = false
                // 加载时一次性预计算匹配键并按 url 预排序；此后过滤免再排序、免重复正则编译。
                searchRows = list.map { SearchRow(it, UrlPatternUtils.buildEndpointKey(it.url)) }
                    .sortedBy { it.item.url }
                totalItems = list.size
                scanInfo = service.lastRebuildInfo
                setControlsEnabled(true)
                applyFilter(textField.text)
            }
            .submit(ForkJoinPool.commonPool())
    }

    private fun setControlsEnabled(enabled: Boolean) {
        incrementalButton.isEnabled = enabled
        fullScanButton.isEnabled = enabled
    }

    private fun scheduleFilter() {
        filterTimer.stop()
        filterTimer.restart()
    }

    private fun applyFilter(query: String) {
        val (visible, matched) = filterItems(query)
        listModel.replaceAll(visible)
        val normalized = UrlPatternUtils.normalizeUserInput(query)
        val emptyQuery = StringUtil.isEmptyOrSpaces(query) || normalized == "/" || normalized.isEmpty()
        updateStatus(matched, visible.size, emptyQuery)
        if (visible.isEmpty() && emptyQuery) {
            // 空输入且未开启“一直显示”时给出提示
            statusLabel.toolTipText = "输入以搜索；或在设置中开启“打开时一直显示结果列表”"
        }
    }

    private fun updateStatus(matched: Int, visible: Int, emptyQuery: Boolean) {
        val sb = StringBuilder("项目: ${project.name}")
        if (emptyQuery) {
            sb.append(" | 已加载: $totalItems 条")
            if (!RestfulToolkitSettings.getInstance().alwaysShowResultList) sb.append("（输入以搜索）")
        } else {
            sb.append(" | 命中: $matched")
            if (visible < matched) sb.append("，显示前 $visible（请细化查询）")
        }
        if (!StringUtil.isEmptyOrSpaces(scanInfo)) sb.append(" | 扫描: $scanInfo")
        statusLabel.text = sb.toString()
    }

    /** 返回（可显示条目, 命中总数）；可显示条目受设置上限约束，命中总数仅用于状态提示。 */
    private fun filterItems(query: String): Pair<List<RestServiceItem>, Int> {
        val limit = RestfulToolkitSettings.getInstance().maxVisibleResults
        val methodFilter = methodCombo.selectedItem as? HttpMethod
        // searchRows 已按 url 预排序，filter 保序，故结果天然有序，无需再排序。
        val base = if (methodFilter == null) searchRows else searchRows.filter { it.item.method == methodFilter }

        val normalized = UrlPatternUtils.normalizeUserInput(query)
        val isEmptyQuery = StringUtil.isEmptyOrSpaces(query) || normalized == "/" || normalized.isEmpty()
        if (isEmptyQuery) {
            if (!RestfulToolkitSettings.getInstance().alwaysShowResultList) return Pair(emptyList(), 0)
            return Pair(base.asSequence().map { it.item }.take(limit).toList(), base.size)
        }
        // 每轮查询只归一化/建候选一次，热路径对每条只做纯字符串比较 + 预编译正则匹配
        val matcher = UrlPatternUtils.compileQuery(query)
        val matched = base.filter { matcher.matches(it.key) }
        return Pair(matched.asSequence().map { it.item }.take(limit).toList(), matched.size)
    }

    /** 预计算行：条目 + 其匹配键。 */
    private class SearchRow(val item: RestServiceItem, val key: UrlPatternUtils.EndpointKey)

    /** 列表渲染：方法图标 + url + 灰色“(模块 类#方法)”后缀。 */
    private class RestItemRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val item = value as? RestServiceItem
            icon = item?.let { ToolkitIcons.Method.get(it.method) }
            val location = item?.locationText.orEmpty()
            text = if (item == null) "" else "<html>${escape(item.url)} <font color='gray'>${escape(location)}</font></html>"
            background = if (isSelected) list.selectionBackground else list.background
            foreground = if (isSelected) list.selectionForeground else list.foreground
            return this
        }

        private fun escape(value: String): String =
            value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    companion object {
        /** 输入防抖间隔（ms）：停手后再过滤，避免每个字符触发全量重算与重绘。 */
        private const val FILTER_DEBOUNCE_MS = 120
    }
}
