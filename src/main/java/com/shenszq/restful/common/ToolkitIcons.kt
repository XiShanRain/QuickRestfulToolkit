package com.shenszq.restful.common

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.shenszq.restful.method.HttpMethod
import javax.swing.Icon

object ToolkitIcons {
    val SERVICE = IconLoader.getIcon("/icons/service.png", ToolkitIcons::class.java)

    object Method {
        val GET = IconLoader.getIcon("/icons/method/g.png", ToolkitIcons::class.java)
        val POST = IconLoader.getIcon("/icons/method/p.png", ToolkitIcons::class.java)
        val PUT = IconLoader.getIcon("/icons/method/p2.png", ToolkitIcons::class.java)
        val PATCH = IconLoader.getIcon("/icons/method/p3.png", ToolkitIcons::class.java)
        val DELETE = IconLoader.getIcon("/icons/method/d.png", ToolkitIcons::class.java)
        val UNDEFINED = IconLoader.getIcon("/icons/method/undefined.png", ToolkitIcons::class.java)

        fun get(method: HttpMethod?): Icon = when (method) {
            HttpMethod.GET -> GET
            HttpMethod.POST -> POST
            HttpMethod.PUT -> PUT
            HttpMethod.PATCH -> PATCH
            HttpMethod.DELETE -> DELETE
            null -> UNDEFINED
            else -> AllIcons.Actions.Search
        }
    }
}
