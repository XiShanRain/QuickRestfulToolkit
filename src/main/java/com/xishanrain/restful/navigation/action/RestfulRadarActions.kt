package com.xishanrain.restful.navigation.action

/**
 * 插件内 action ID 常量。
 *
 * 必须与其他 RestfulToolkit 系插件（如 `me.jinghong.restful.toolkit`）区分：平台里 action ID
 * 全局唯一，重复时后注册方会抛
 * `PluginException: ID 'xxx' is already taken by action ...`，并导致
 * `ActionManager.getAction(id)` 拿到对方的实例（表现为“设置不生效”）。
 * 因此统一使用 `RestfulRadar.` 前缀，新增 action 请沿用该约定。
 */
object RestfulRadarActions {
    const val GOTO_SERVICE = "RestfulRadar.GotoService"
    const val RESCAN_REST = "RestfulRadar.RescanRest"
    const val SWITCH_SEARCH_UI = "RestfulRadar.SwitchSearchUi"
    const val GOTO_SERVICE_DIALOG = "RestfulRadar.GotoServiceDialog"
    const val GOTO_SERVICE_POPUP = "RestfulRadar.GotoServicePopup"
}
