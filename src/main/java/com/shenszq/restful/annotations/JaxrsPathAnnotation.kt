package com.shenszq.restful.annotations

enum class JaxrsPathAnnotation(
    override val shortName: String,
    override val qualifiedName: String
) : PathMappingAnnotation {
    JAVAX_PATH("Path", "javax.ws.rs.Path"),
    JAKARTA_PATH("Path", "jakarta.ws.rs.Path")
}
