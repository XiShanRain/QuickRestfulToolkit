package com.shenszq.restful.settings

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.GridLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * RestfulRadar 设置页：
 * - 首选搜索 UI（对话框 / 弹层兜底）与窗口内切换、Ctrl+Alt+Shift+/ 全局切换动作同步翻转
 * - 对话框打开时是否一直显示结果列表
 * - 补漏扫描开关（默认开）
 * - 方法级 mapping 反查（默认关）
 * - 增量缓存开关（默认开）
 * - es.exe 路径（仅用于加速补漏枚举，可用则用、否则自动回退文件系统）
 * - 快捷键：跳转到 IDE Keymap 面板配置
 */
class RestfulToolkitConfigurable : Configurable {

    private val settings get() = RestfulToolkitSettings.getInstance()
    private var rootPanel: JPanel? = null
    private var esPathField: JBTextField? = null
    private var gapFillCheck: JBCheckBox? = null
    private var methodLevelCheck: JBCheckBox? = null
    private var cacheCheck: JBCheckBox? = null
    private var dialogPreferredCheck: JBCheckBox? = null
    private var alwaysShowListCheck: JBCheckBox? = null
    private var maxVisibleCombo: javax.swing.JComboBox<Int>? = null

    override fun getDisplayName(): String = "RestfulRadar"

    /** 插件版本号：构建时由 patchPluginXml 写入 plugin.xml，运行时从描述符读取，随发布包自动更新。 */
    private fun pluginVersion(): String =
        PluginManagerCore.getPlugin(PluginId.getId("com.shenszq.restful.restfulradar"))?.version ?: "未知"

    override fun createComponent(): JComponent {
        gapFillCheck = JBCheckBox("为未被索引覆盖的文件启用补漏扫描（推荐）", settings.gapFillEnabled)
        methodLevelCheck = JBCheckBox("额外按方法级 Mapping 注解反查所在类（最大化召回，稍慢）", settings.methodLevelScanEnabled)
        cacheCheck = JBCheckBox("使用增量缓存（按修改时间跳过未变化文件）", settings.useCache)
        dialogPreferredCheck = JBCheckBox("使用对话框式搜索作为首选入口（取消则回退弹层；可在窗口内 Ctrl+Alt+P / Ctrl+Alt+Shift+/ 同步翻转）", settings.dialogSearchPreferred)
        alwaysShowListCheck = JBCheckBox("对话框打开时一直显示结果列表（取消则输入后显示）", settings.alwaysShowResultList)

        val visibleOptions = listOf(10, 20, 50, 100, 200)
        val currentMax = settings.maxVisibleResults
        val options = if (currentMax in visibleOptions) visibleOptions else (visibleOptions + currentMax).sorted()
        maxVisibleCombo = javax.swing.JComboBox(options.toTypedArray())
        maxVisibleCombo?.selectedItem = currentMax

        esPathField = JBTextField(settings.esPath, 20)
        val browse = JButton("浏览...")
        browse.addActionListener {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.FILES_ONLY }
            if (chooser.showOpenDialog(rootPanel) == JFileChooser.APPROVE_OPTION) {
                esPathField?.text = chooser.selectedFile.absolutePath
            }
        }
        val detect = JButton("自动检测")
        detect.addActionListener {
            RestfulToolkitSettings.detectEsExecutable()?.let { esPathField?.text = it }
        }
        val pathPanel = JPanel(BorderLayout(4, 0))
        pathPanel.add(esPathField, BorderLayout.CENTER)
        val btns = JPanel(GridLayout(1, 2, 4, 0))
        btns.add(browse); btns.add(detect)
        pathPanel.add(btns, BorderLayout.EAST)

        val keymapBtn = JButton("在 Keymap 中配置 “Go to REST Service” 快捷键")
        keymapBtn.addActionListener {
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Keymap")
        }

        // 用 GridBagLayout 代替 GridLayout(0,2)：GridLayout 会强制两列等宽，
        // 导致标签与控件之间大片空白、窗口拉小时右侧控件被裁掉。
        // GridBag：标签列按内容自适应宽度，控件列 weightx=1 吃掉剩余空间。
        val panel = JPanel(GridBagLayout())
        panel.border = EmptyBorder(8, 8, 8, 8)
        rootPanel = panel

        // 跨两列的整行（复选框，自带长文本）
        fun addFullRow(component: JComponent, top: Int) {
            val gc = GridBagConstraints()
            gc.gridx = 0
            gc.gridy = GridBagConstraints.RELATIVE
            gc.gridwidth = 2
            gc.weightx = 1.0
            gc.fill = GridBagConstraints.HORIZONTAL
            gc.anchor = GridBagConstraints.NORTHWEST
            gc.insets = Insets(top, 0, 0, 0)
            panel.add(component, gc)
        }

        // 标签 + 控件两列；fill=true 时控件横向拉伸填满右列
        fun addLabeledRow(labelText: String, component: JComponent, fill: Boolean, top: Int) {
            val lc = GridBagConstraints()
            lc.gridx = 0
            lc.gridy = GridBagConstraints.RELATIVE
            lc.gridwidth = 1
            lc.weightx = 0.0
            lc.fill = GridBagConstraints.NONE
            lc.anchor = GridBagConstraints.NORTHWEST
            lc.insets = Insets(top, 0, 0, 8)
            panel.add(JLabel(labelText), lc)

            val fc = GridBagConstraints()
            fc.gridx = 1
            fc.gridy = GridBagConstraints.RELATIVE
            fc.gridwidth = 1
            fc.weightx = 1.0
            fc.fill = if (fill) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
            fc.anchor = GridBagConstraints.NORTHWEST
            fc.insets = Insets(top, 0, 0, 0)
            panel.add(component, fc)
        }

        addLabeledRow("插件版本:", JLabel(pluginVersion()), false, 0)
        addFullRow(dialogPreferredCheck!!, 8)
        addFullRow(alwaysShowListCheck!!, 8)
        addLabeledRow("结果列表最多显示:", maxVisibleCombo!!, false, 8)
        addFullRow(gapFillCheck!!, 8)
        addFullRow(methodLevelCheck!!, 8)
        addFullRow(cacheCheck!!, 8)
        addLabeledRow("es.exe 路径:", pathPanel, true, 8)
        addLabeledRow("快捷键:", keymapBtn, true, 8)

        // Settings 页会把组件按首选大小在垂直方向居中摆放，行内 anchor 管不到这一层；
        // 用 BorderLayout 包装层把内容面板钉在顶部（NORTH 占满宽度、贴顶）。
        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.NORTH)
        return wrapper
    }

    override fun isModified(): Boolean {
        if (gapFillCheck?.isSelected != settings.gapFillEnabled) return true
        if (methodLevelCheck?.isSelected != settings.methodLevelScanEnabled) return true
        if (cacheCheck?.isSelected != settings.useCache) return true
        if (dialogPreferredCheck?.isSelected != settings.dialogSearchPreferred) return true
        if (alwaysShowListCheck?.isSelected != settings.alwaysShowResultList) return true
        if (maxVisibleCombo?.selectedItem != settings.maxVisibleResults) return true
        if ((esPathField?.text ?: "") != settings.esPath) return true
        return false
    }

    override fun apply() {
        settings.gapFillEnabled = gapFillCheck?.isSelected == true
        settings.methodLevelScanEnabled = methodLevelCheck?.isSelected == true
        settings.useCache = cacheCheck?.isSelected == true
        settings.dialogSearchPreferred = dialogPreferredCheck?.isSelected == true
        settings.alwaysShowResultList = alwaysShowListCheck?.isSelected == true
        (maxVisibleCombo?.selectedItem as? Int)?.let { settings.maxVisibleResults = it }
        settings.esPath = esPathField?.text ?: ""
        // 设置变化后让各项目的缓存失效并后台重建
        ProjectManager.getInstance().openProjects.forEach { project ->
            val service = com.shenszq.restful.scan.RestScanService.getInstance(project)
            service.invalidate()
            service.warmUp()
        }
    }

    override fun reset() {
        gapFillCheck?.isSelected = settings.gapFillEnabled
        methodLevelCheck?.isSelected = settings.methodLevelScanEnabled
        cacheCheck?.isSelected = settings.useCache
        dialogPreferredCheck?.isSelected = settings.dialogSearchPreferred
        alwaysShowListCheck?.isSelected = settings.alwaysShowResultList
        maxVisibleCombo?.selectedItem = settings.maxVisibleResults
        esPathField?.text = settings.esPath
    }
}
