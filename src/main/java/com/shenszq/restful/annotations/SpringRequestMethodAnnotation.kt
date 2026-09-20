package com.shenszq.restful.annotations

enum class SpringRequestMethodAnnotation(
    val qualifiedName: String,
    val methodName: String?
) {
    REQUEST_MAPPING("org.springframework.web.bind.annotation.RequestMapping", null),
    GET_MAPPING("org.springframework.web.bind.annotation.GetMapping", "GET"),
    POST_MAPPING("org.springframework.web.bind.annotation.PostMapping", "POST"),
    PUT_MAPPING("org.springframework.web.bind.annotation.PutMapping", "PUT"),
    DELETE_MAPPING("org.springframework.web.bind.annotation.DeleteMapping", "DELETE"),
    PATCH_MAPPING("org.springframework.web.bind.annotation.PatchMapping", "PATCH");

    companion object {
        fun getByQualifiedName(value: String): SpringRequestMethodAnnotation? =
            entries.find { it.qualifiedName == value }
    }
}
