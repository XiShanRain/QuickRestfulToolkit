package com.shenszq.restful.annotations

enum class SpringControllerAnnotation(
    override val shortName: String,
    override val qualifiedName: String
) : PathMappingAnnotation {
    CONTROLLER("Controller", "org.springframework.stereotype.Controller"),
    REST_CONTROLLER("RestController", "org.springframework.web.bind.annotation.RestController")
}
