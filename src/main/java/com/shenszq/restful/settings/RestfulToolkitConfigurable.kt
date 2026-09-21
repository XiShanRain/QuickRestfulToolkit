package com.shenszq.restful.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * QuickRestfulToolkit 设置页：
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

    override fun getDisplayName(): String = "QuickRestfulToolkit"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridLayout(0, 2, 8, 8))
        rootPanel = panel

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

        esPathField = JBTextField(RestfulToolkitSettings.getInstance().esPath, 40)
        val browse = JButton("浏览...")
        browse.addActionListener {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.FILES_ONLY }
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                esPathField?.text = chooser.selectedFile.absolutePath
            }
        }
        val detect = JButton("自动检测")
        detect.addActionListener {
            RestfulToolkitSettings.detectEsExecutable()?.let { esPathField?.text = it }
        }
        val pathPanel = JPanel(BorderLayout(4, 0))
        pathPanel.add(esPathField, BorderLayout.CENTER)
        val btns = JPanel(GridLayout(0, 2, 4, 0))
        btns.add(browse); btns.add(detect)
        pathPanel.add(btns, BorderLayout.EAST)

        val keymapBtn = JButton("在 Keymap 中配置 “Go to REST Service” 快捷键")
        keymapBtn.addActionListener {
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Keymap")
        }

        panel.add(JLabel()); panel.add(dialogPreferredCheck)
        panel.add(JLabel()); panel.add(alwaysShowListCheck)
        panel.add(JLabel("结果列表最多显示:")); panel.add(maxVisibleCombo)
        panel.add(JLabel()); panel.add(gapFillCheck)
        panel.add(JLabel()); panel.add(methodLevelCheck)
        panel.add(JLabel()); panel.add(cacheCheck)
        panel.add(JLabel("es.exe 路径:")); panel.add(pathPanel)
        panel.add(JLabel("快捷键:")); panel.add(keymapBtn)

        return panel
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
