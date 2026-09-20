package com.shenszq.restful.method

data class RequestPath(var path: String, val method: String?) {
    fun concat(classRequestPath: RequestPath) {
        var classUri = classRequestPath.path
        var methodUri = path
        if (!classUri.startsWith("/")) classUri = "/$classUri"
        if (!classUri.endsWith("/")) classUri = "$classUri/"
        if (methodUri.startsWith("/")) methodUri = methodUri.substring(1)
        path = "$classUri$methodUri"
    }
}
