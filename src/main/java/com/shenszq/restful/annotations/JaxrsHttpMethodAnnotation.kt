package com.shenszq.restful.annotations

enum class JaxrsHttpMethodAnnotation(
    val qualifiedName: String,
    val methodName: String
) {
    JAVAX_GET("javax.ws.rs.GET", "GET"),
    JAVAX_POST("javax.ws.rs.POST", "POST"),
    JAVAX_PUT("javax.ws.rs.PUT", "PUT"),
    JAVAX_DELETE("javax.ws.rs.DELETE", "DELETE"),
    JAVAX_HEAD("javax.ws.rs.HEAD", "HEAD"),
    JAVAX_OPTIONS("javax.ws.rs.OPTIONS", "OPTIONS"),
    JAVAX_PATCH("javax.ws.rs.PATCH", "PATCH"),
    JAKARTA_GET("jakarta.ws.rs.GET", "GET"),
    JAKARTA_POST("jakarta.ws.rs.POST", "POST"),
    JAKARTA_PUT("jakarta.ws.rs.PUT", "PUT"),
    JAKARTA_DELETE("jakarta.ws.rs.DELETE", "DELETE"),
    JAKARTA_HEAD("jakarta.ws.rs.HEAD", "HEAD"),
    JAKARTA_OPTIONS("jakarta.ws.rs.OPTIONS", "OPTIONS"),
    JAKARTA_PATCH("jakarta.ws.rs.PATCH", "PATCH");

    companion object {
        fun getByQualifiedName(value: String): JaxrsHttpMethodAnnotation? =
            entries.find { it.qualifiedName == value }
    }
}
