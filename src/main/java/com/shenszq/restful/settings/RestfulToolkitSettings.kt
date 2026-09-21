package com.shenszq.restful.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.io.File

/**
 * 插件全局设置（应用级持久化）。
 *
 * 采用单一入口策略，不再提供“搜索模式切换”：
 * - 主路径始终是增强后的 PSI 索引搜索（含 Feign 接口召回）；
 * - [gapFillEnabled] 控制是否对未被索引覆盖的 .java 文件做补漏扫描；
 * - es.exe 仅用于加速补漏阶段的文件枚举，可用则用、不可用自动回退文件系统递归。
 */
@Service
@State(name = "RestfulToolkitSettings", storages = [Storage("restful-toolkit.xml")])
class RestfulToolkitSettings : PersistentStateComponent<RestfulToolkitSettings.State> {

    class State {
        var gapFillEnabled: Boolean = true
        var methodLevelScanEnabled: Boolean = false
        var useCache: Boolean = true
        var esPath: String = ""
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
