package com.xishanrain.restful.navigation.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JTabbedPane
import javax.swing.JTextArea

/**
 * 请求调试面板（对齐原版 RestServiceDetail）：
 * 顶部一行 [method][url][Send]；下方页签栏动态展示 RequestParams / RequestBody / Response。
 *
 * - 初始/重置态：GET + URL + 提示页签 "Try press 'Ctrl(Cmd) Enter'"；
 * - 单击服务节点：填充完整 URL 与参数页签，RequestBody 非空时追加该页签；
 * - Send：替换路径变量、拼接 query 参数，有 RequestBody 走 POST JSON，否则按 method 发送；
 * - 文本区：Ctrl(Cmd)+Enter 美化 JSON，双击复制全文。
 */
class RestServiceDetailPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val methodField = JBTextField("GET")
    private val urlField = JBTextField("URL")
    private val sendButton = JButton("Send")
    private val tabbedPane = JTabbedPane()

    private var requestParamsArea: JTextArea? = null
    private var requestBodyArea: JTextArea? = null
    private var responseArea: JTextArea? = null

    init {
        border = JBUI.Borders.empty(4)

        methodField.preferredSize = Dimension(JBUI.scale(80), methodField.preferredSize.height)
        methodField.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = methodField.selectAll()
        })
        urlField.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = urlField.selectAll()
        })
        sendButton.addActionListener { send() }

        val urlRow = JBPanel<JBPanel<*>>(BorderLayout(6, 0))
        urlRow.add(methodField, BorderLayout.WEST)
        urlRow.add(urlField, BorderLayout.CENTER)
        urlRow.add(sendButton, BorderLayout.EAST)

        add(urlRow, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)

        initTab()
    }

    fun setMethodValue(method: String?) {
        methodField.text = method ?: "GET"
    }

    fun setUrlValue(url: String?) {
        urlField.text = url ?: ""
    }

    /** 把 buildParamString 产物（"k=v&k2=v2"）转成 "k : v" 行填入 RequestParams 页签（对齐原版）。 */
    fun addRequestParamsTab(requestParams: String?) {
        val builder = StringBuilder()
        if (!requestParams.isNullOrBlank()) {
            for (pair in requestParams.split("&")) {
                if (pair.isEmpty()) continue
                val param = pair.substringBefore('=')
                val value = if (pair.contains('=')) pair.substring(param.length + 1) else ""
                builder.append(param).append(" : ").append(value).append('\n')
            }
        }
        val area = requestParamsArea ?: createTextArea("").also { requestParamsArea = it }
        area.text = builder.toString()
        addTab("RequestParams", area)
    }

    fun addRequestBodyTabPanel(text: String) {
        val area = requestBodyArea ?: createTextArea("").also { requestBodyArea = it }
        area.text = text
        addTab("RequestBody", area)
    }

    fun addResponseTabPanel(text: String) {
        val area = responseArea ?: createTextArea("").also { responseArea = it }
        area.text = text
        addTab("Response", area)
    }

    fun resetRequestTabbedPane() {
        tabbedPane.removeAll()
        requestParamsArea?.text = ""
        requestBodyArea?.text = ""
        responseArea?.text = ""
    }

    /** 重置态的提示页签（对齐原版：标题即操作提示，内容为示例 JSON）。 */
    fun initTab() {
        val area = createTextArea("{'key':'value'}")
        addTab("Try press 'Ctrl(Cmd) Enter'", area)
    }

    private fun addTab(title: String, area: JTextArea) {
        val existing = findScrollPane(area)
        if (existing != null) {
            tabbedPane.selectedComponent = existing
            return
        }
        val scrollPane = JBScrollPane(
            area,
            JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        tabbedPane.addTab(title, scrollPane)
        tabbedPane.selectedComponent = scrollPane
    }

    private fun findScrollPane(area: JTextArea): JBScrollPane? {
        for (index in 0 until tabbedPane.tabCount) {
            val component = tabbedPane.getComponentAt(index)
            if (component is JBScrollPane && component.viewport.view === area) return component
        }
        return null
    }

    private fun createTextArea(initialText: String): JTextArea {
        val area = JTextArea(initialText)
        area.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(13))

        area.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && (e.isControlDown || e.isMetaDown)) {
                    val pretty = RestJson.pretty(area.text) ?: return
                    area.text = pretty
                }
            }
        })
        area.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount > 1) {
                    CopyPasteManager.getInstance().setContents(StringSelection(area.text))
                }
            }
        })
        return area
    }

    private fun send() {
        val url = urlField.text
        if (url.isBlank()) return
        val method = methodField.text
        val paramsText = requestParamsArea?.text.orEmpty()
        val bodyText = requestBodyArea?.text.orEmpty()

        object : Task.Backgroundable(project, "Sending Request", false) {
            override fun run(indicator: ProgressIndicator) {
                var target = url
                val paramMap = RestParamTextSupport.toParamMap(paramsText)
                // 路径变量替换：{id}/… 形式的占位符用参数值填充
                for ((key, value) in paramMap) {
                    target = target.replaceFirst(
                        Regex("\\{(${Regex.escape(key)}[\\s\\S]*?)\\}"),
                        Regex.escapeReplacement(value)
                    )
                }
                val params = RestParamTextSupport.toRequestParam(paramMap)
                if (params.isNotEmpty()) {
                    target += if (target.contains("?")) "&$params" else "?$params"
                }

                val response = if (bodyText.isNotBlank()) {
                    RestHttpClient.postRequestBodyWithJson(target, bodyText)
                } else {
                    RestHttpClient.request(target, method)
                }

                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) addResponseTabPanel(response)
                }
            }
        }.queue()
    }
}
