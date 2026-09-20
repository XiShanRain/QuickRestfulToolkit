package com.shenszq.restful.method

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT;

    companion object {
        fun getByRequestMethod(method: String?): HttpMethod? {
            if (method.isNullOrEmpty()) return null
            val normalized = method.trim()
            val dotIndex = normalized.lastIndexOf('.')
            val cleanName = if (dotIndex >= 0 && dotIndex + 1 < normalized.length) {
                normalized.substring(dotIndex + 1)
            } else normalized
            return try { valueOf(cleanName.uppercase()) } catch (_: IllegalArgumentException) { null }
        }
    }
}
