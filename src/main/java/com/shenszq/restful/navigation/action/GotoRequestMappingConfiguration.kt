package com.xishanrain.restful.navigation.action

import com.intellij.ide.util.gotoByName.ChooseByNameFilterConfiguration
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.xishanrain.restful.method.HttpMethod

// @State 的 name 会写入项目 workspace.xml，必须与其他 RestfulToolkit 系插件区分（原版同名时
// 两个插件的 HTTP 方法过滤选择会互相覆盖）。
@State(name = "RestfulRadar.GotoRequestMappingConfiguration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GotoRequestMappingConfiguration : ChooseByNameFilterConfiguration<HttpMethod>() {
    companion object {
        fun getInstance(project: Project): GotoRequestMappingConfiguration =
            project.getService(GotoRequestMappingConfiguration::class.java)
    }

    override fun nameForElement(type: HttpMethod): String = type.name
}
