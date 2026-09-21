package com.shenszq.restful.scan

import com.intellij.openapi.startup.ProjectActivity

/**
 * 项目打开后在后台预热 REST 路径缓存，使首次 Ctrl+Alt+/ 也能瞬间出结果。
 * 仅调度非阻塞读任务后立即返回，不阻塞 EDT，也不影响弹窗的同步兜底构建。
 */
class RestScanStartupActivity : ProjectActivity {
    override suspend fun execute(context: com.intellij.openapi.project.Project) {
        RestScanService.getInstance(context).warmUp()
    }
}
