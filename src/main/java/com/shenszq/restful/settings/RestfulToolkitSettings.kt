package com.xishanrain.restful.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.io.File

/**
 * 插件全局设置（应用级持久化）。
 *
 * 搜索入口策略：
 * - [dialogSearchPreferred] 表示 Ctrl+Alt+/ **默认打开的窗口**（设置页的“默认窗口”选项）：
 *   为 true 用对话框式搜索（迁移自 quick-restful 的交互形态），为 false 用右侧 RestfulRadar
 *   工具窗口（按模块浏览）；
 * - [alternateSearchUiOnInvoke] 为 true 时，Ctrl+Alt+/ **每次按下都在「模态对话框」与
 *   「右侧 RestfulRadar 工具窗口」之间交替**（按一次面板、再按一次对话框…），此时忽略
 *   默认窗口；默认关闭，即默认行为是“固定打开默认窗口”；
 * - [alwaysShowResultList] 控制对话框打开时是否空查询即展示全量列表（仅作用于对话框，
 *   弹层由 IDE 框架决定，本来就需输入才出结果）；
 * - [gapFillEnabled] 控制是否对未被索引覆盖的 .java 文件做补漏扫描；
 * - es.exe 仅用于加速补漏阶段的文件枚举，可用则用、不可用自动回退文件系统递归。
 */
// @State 的 name / 存储文件都带插件自己的命名空间：原版 RestfulToolkit 系插件若用
// "RestfulToolkitSettings" / "restful-toolkit.xml"，两者同时安装会互相覆盖设置。
@Service
@State(name = "RestfulRadarSettings", storages = [Storage("restful-radar.xml")])
class RestfulToolkitSettings : PersistentStateComponent<RestfulToolkitSettings.State> {

    class State {
        var gapFillEnabled: Boolean = true
        var methodLevelScanEnabled: Boolean = false
        var useCache: Boolean = true
        var esPath: String = ""
        var dialogSearchPreferred: Boolean = true
        var alternateSearchUiOnInvoke: Boolean = false
        var alwaysShowResultList: Boolean = false
        var maxVisibleResults: Int = 20
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    /** 是否为未索引文件启用补漏扫描（默认开）。 */
    var gapFillEnabled: Boolean
        get() = myState.gapFillEnabled
        set(value) { myState.gapFillEnabled = value }

    /** 是否额外按方法级 mapping 注解反查所在类（默认关，最大化召回时开启）。 */
    var methodLevelScanEnabled: Boolean
        get() = myState.methodLevelScanEnabled
        set(value) { myState.methodLevelScanEnabled = value }

    /** 是否使用增量缓存（按文件修改时间跳过未变化文件的重复解析）。 */
    var useCache: Boolean
        get() = myState.useCache
        set(value) { myState.useCache = value }

    /** es.exe 路径；为空时尝试自动检测。 */
    var esPath: String
        get() = myState.esPath.ifBlank { detectEsExecutable() ?: "" }
        set(value) { myState.esPath = value }

    /**
     * Ctrl+Alt+/ 是否每次按下都在「模态对话框」与「右侧 RestfulRadar 工具窗口」之间交替（默认关）。
     * 关闭时（默认）由 [dialogSearchPreferred] 决定“默认窗口”；开启时忽略它，每次翻转。
     */
    var alternateSearchUiOnInvoke: Boolean
        get() = myState.alternateSearchUiOnInvoke
        set(value) { myState.alternateSearchUiOnInvoke = value }

    /** 默认打开的窗口：true 用对话框，false 用右侧 RestfulRadar 工具窗口（交替关闭时生效）。 */
    var dialogSearchPreferred: Boolean
        get() = myState.dialogSearchPreferred
        set(value) { myState.dialogSearchPreferred = value }

    /** 对话框打开时是否空查询即展示全量列表（默认关，输入后显示）。 */
    var alwaysShowResultList: Boolean
        get() = myState.alwaysShowResultList
        set(value) { myState.alwaysShowResultList = value }

    /** 对话框结果列表一次最多渲染的条目数（性能保护，默认 20）。 */
    var maxVisibleResults: Int
        get() = myState.maxVisibleResults.coerceAtLeast(1)
        set(value) { myState.maxVisibleResults = value }

    companion object {
        fun getInstance(): RestfulToolkitSettings = service()

        /** 自动检测 es.exe：先查 PATH，再探测 Everything 常见安装目录。 */
        fun detectEsExecutable(): String? {
            val pathEnv = System.getenv("PATH")
            if (pathEnv != null) {
                for (dir in pathEnv.split(File.pathSeparator)) {
                    val exe = File(dir, "es.exe")
                    if (exe.exists() && exe.canExecute()) return exe.absolutePath
                }
            }
            val candidates = mutableListOf<String>()
            System.getenv("ProgramFiles")?.let { candidates.add(it) }
            System.getenv("ProgramFiles(x86)")?.let { candidates.add(it) }
            System.getenv("LOCALAPPDATA")?.let { candidates.add("$it\\Programs") }
            for (root in candidates) {
                val exe = File(root, "Everything\\es.exe")
                if (exe.exists() && exe.canExecute()) return exe.absolutePath
            }
            return null
        }
    }
}
