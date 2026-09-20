package com.shenszq.restful.navigation.action

import com.intellij.ide.util.gotoByName.ChooseByNameFilterConfiguration
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.shenszq.restful.method.HttpMethod

@State(name = "GotoRequestMappingConfiguration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GotoRequestMappingConfiguration : ChooseByNameFilterConfiguration<HttpMethod>() {
    companion object {
        fun getInstance(project: Project): GotoRequestMappingConfiguration =
            project.getService(GotoRequestMappingConfiguration::class.java)
    }

    override fun nameForElement(type: HttpMethod): String = type.name
}
