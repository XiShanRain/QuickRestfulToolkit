package com.shenszq.restful.navigation.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.toLightMethods
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具窗口请求调试支撑（对齐原版 RestServices 窗口行为，全部零外部依赖实现）：
 * - [RestHostResolver]：服务 host 前缀 http://localhost:{server.port}{context-path}（读模块内 application/bootstrap 配置）；
 * - [PsiClassDefaults]：基础类型默认值；
 * - [RestMethodRequestInfo]：按控制器方法生成请求参数串与 RequestBody JSON 骨架；
 * - [RestParamTextSupport]："k : v" 文本与参数表互转（Send 时解析 RequestParams 页签）；
 * - [RestHttpClient]：Send 按钮的 HTTP 发送（HttpURLConnection 替代原版 Apache HttpClient）；
 * - [RestJson]：JSON 美化与序列化（手写解析器替代原版 Gson，规避运行期依赖）。
 */
internal object RestHostResolver {

    const val DEFAULT_URI = "http://localhost:8080"

    private const val SCHEME = "http://"
    private const val HOST = "localhost"
    private const val DEFAULT_PORT = "8080"
    private const val KEY_SPRING_PROFILE = "spring.profiles.active"
    private const val KEY_SERVER_PORT = "server.port"
    private const val KEY_CONTEXT_PATH = "server.context-path"
    private const val KEY_SERVLET_CONTEXT_PATH = "server.servlet.context-path"

    private val PLACEHOLDER_REGEX = Regex("\\$\\{[^:}]*:([^}]*)}")

    private val cache = ConcurrentHashMap<Module, String>()

    /** 刷新时清空缓存（配置文件可能已变化）。 */
    fun invalidate() {
        cache.clear()
    }

    /** 模块的 host 前缀；模块为空或读不到配置时退回 http://localhost:8080。 */
    fun serviceHostPrefix(module: Module?): String {
        if (module == null) return DEFAULT_URI
        cache[module]?.let { return it }
        if (DumbService.isDumb(module.project)) return DEFAULT_URI
        val computed = computeHostPrefix(module)
        cache[module] = computed
        return computed
    }

    private fun computeHostPrefix(module: Module): String = runCatching {
        ReadAction.compute<String, RuntimeException> {
            val activeProfile = findPropertyValue(module, KEY_SPRING_PROFILE)
            val port = findPropertyValue(module, KEY_SERVER_PORT, activeProfile)
                .takeIf { it.isNotBlank() } ?: DEFAULT_PORT
            val contextPath = findPropertyValue(module, KEY_CONTEXT_PATH, activeProfile)
                .ifBlank { findPropertyValue(module, KEY_SERVLET_CONTEXT_PATH, activeProfile) }
            SCHEME + HOST + ":" + port + normalizeContextPath(contextPath)
        }
    }.getOrDefault(DEFAULT_URI)

    private fun normalizeContextPath(path: String): String = when {
        path.isBlank() -> ""
        path.startsWith("/") -> path
        else -> "/$path"
    }

    /**
     * 查找配置值：profile 文件优先，其次默认文件；文件顺序 application.properties →
     * application.yml → bootstrap.properties → bootstrap.yml（对齐原版 PropertiesHandler）。
     */
    private fun findPropertyValue(module: Module, key: String, activeProfile: String? = null): String {
        val profileSuffix = activeProfile?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
        val project = module.project
        val scope = GlobalSearchScope.moduleScope(module)
        for (configName in listOf("application", "bootstrap")) {
            for (extension in listOf("properties", "yml")) {
                val fileName = configName + profileSuffix + "." + extension
                val files = runCatching {
                    FilenameIndex.getFilesByName(project, fileName, scope)
                }.getOrNull() ?: continue
                for (file in files) {
                    val value = if (extension == "properties") {
                        parseProperties(file.text)[key]
                    } else {
                        parseYaml(file.text)[key]
                    }
                    if (value != null) return cleanPlaceholder(value)
                }
            }
        }
        return ""
    }

    /** server.port=${PORT:8080} 之类的占位符：取默认值。 */
    private fun cleanPlaceholder(value: String): String {
        if (!value.contains("\${") || !value.contains(":")) return value
        val match = PLACEHOLDER_REGEX.find(value) ?: return value
        return match.groupValues[1]
    }

    private fun parseProperties(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
            val separatorIndex = line.indexOfFirst { it == '=' || it == ':' }
            if (separatorIndex <= 0) continue
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            result[key] = value
        }
        return result
    }

    /** 轻量 YAML 读取：按缩进栈还原 "a.b.c" 扁平键（替代原版 snakeyaml，只服务配置值查询）。 */
    private fun parseYaml(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        val stack = ArrayList<Pair<Int, String>>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd()
            val trimmed = line.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---")) continue
            val indent = line.length - trimmed.length
            while (stack.isNotEmpty() && stack.last().first >= indent) {
                stack.removeAt(stack.size - 1)
            }
            val separatorIndex = trimmed.indexOf(':')
            if (separatorIndex <= 0) continue
            val key = trimmed.substring(0, separatorIndex).trim().trim('"', '\'')
            val rawValue = trimmed.substring(separatorIndex + 1).trim()
            val prefix = stack.joinToString("") { it.second + "." }
            if (rawValue.isEmpty()) {
                stack.add(indent to key)
            } else {
                result[prefix + key] = rawValue.trim('"', '\'')
            }
        }
        return result
    }
}

/** 基础类型默认值（对齐原版 PsiClassHelper.getJavaBaseTypeDefaultValue）。 */
internal object PsiClassDefaults {

    fun get(typeName: String?): Any? {
        val normalized = typeName?.substringAfterLast('.')?.lowercase() ?: return null
        return when (normalized) {
            "byte" -> 1
            "char", "character" -> 'Z'
            "boolean" -> true
            "int", "integer" -> 1
            "double" -> 1.0
            "float" -> 1.0f
            "long" -> 1L
            "short" -> 1
            "bigdecimal" -> 1
            "string" -> "demoData"
            "date" -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            else -> null
        }
    }
}

/** 按控制器方法生成请求参数串与 RequestBody JSON（对齐原版 PsiMethodHelper/PsiClassHelper）。 */
internal object RestMethodRequestInfo {

    private const val DEFAULT_RECURSIVE_COUNT = 1

    private val SERVLET_PARAM_TYPES = setOf(
        "javax.servlet.http.HttpServletRequest",
        "javax.servlet.http.HttpServletResponse",
        "javax.servlet.ServletRequest",
        "javax.servlet.ServletResponse",
        "jakarta.servlet.http.HttpServletRequest",
        "jakarta.servlet.http.HttpServletResponse",
        "jakarta.servlet.ServletRequest",
        "jakarta.servlet.ServletResponse"
    )

    /** Kotlin 函数元素转 PsiMethod（复用 Java 侧全部逻辑）；非方法元素返回 null。 */
    fun toPsiMethod(element: PsiElement): PsiMethod? = runCatching {
        when (element) {
            is PsiMethod -> element
            else -> element.toLightMethods().firstOrNull()
        }
    }.getOrNull()

    /** 请求参数串 "k=v&k2=v2"（跳过 servlet 参数与 @RequestBody；复杂参数类拍平一层基础字段）。 */
    fun buildParamString(method: PsiMethod): String {
        val paramMap = LinkedHashMap<String, Any?>()
        for (parameter in collectParameters(method)) {
            if (parameter.requestBodyFound) continue
            val baseValue = PsiClassDefaults.get(parameter.type)
            if (baseValue != null) {
                paramMap[parameter.name] = baseValue
                continue
            }
            val paramClass = findClass(method.project, parameter.type) ?: continue
            for (field in paramClass.fields) {
                if (field.hasModifierProperty(PsiModifier.STATIC)) continue
                val fieldValue = PsiClassDefaults.get(field.type.presentableText) ?: continue
                paramMap[field.name] = fieldValue
            }
        }
        return paramMap.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    /** 首个 @RequestBody 参数的 JSON 骨架；没有则返回空串。 */
    fun buildRequestBodyJson(method: PsiMethod): String {
        for (parameter in collectParameters(method)) {
            if (parameter.requestBodyFound) {
                return convertClassToJson(method.project, parameter.type)
            }
        }
        return ""
    }

    private data class ParameterInfo(val type: String, val name: String, val requestBodyFound: Boolean)

    private fun collectParameters(method: PsiMethod): List<ParameterInfo> {
        val result = ArrayList<ParameterInfo>()
        for (parameter in method.parameterList.parameters) {
            val canonicalType = parameter.type.canonicalText
            if (canonicalType in SERVLET_PARAM_TYPES) continue
            val requestBodyFound = findAnnotation(parameter, "RequestBody") != null
            // 显式指定名称的注解：value 优先于参数名（含 JAX-RS 的 @PathParam/@QueryParam）
            val declaredName = annotationValue(findAnnotation(parameter, "PathVariable"))
                ?: annotationValue(findAnnotation(parameter, "PathParam"))
                ?: annotationValue(findAnnotation(parameter, "RequestParam"))
                ?: annotationValue(findAnnotation(parameter, "QueryParam"))
            result.add(ParameterInfo(canonicalType, declaredName ?: parameter.name.orEmpty(), requestBodyFound))
        }
        return result
    }

    private fun findAnnotation(parameter: PsiParameter, simpleName: String): PsiAnnotation? {
        val modifierList = parameter.modifierList ?: return null
        return modifierList.annotations.firstOrNull { annotation ->
            val qualifiedName = annotation.qualifiedName
                ?: annotation.nameReferenceElement?.text
                ?: return@firstOrNull false
            qualifiedName == simpleName || qualifiedName.substringAfterLast('.') == simpleName
        }
    }

    private fun annotationValue(annotation: PsiAnnotation?): String? {
        val attributeValue = annotation?.findDeclaredAttributeValue("value") as? PsiLiteralExpression
            ?: return null
        return attributeValue.value as? String
    }

    private fun convertClassToJson(project: Project, className: String): String = runCatching {
        if (className.contains("List<")) {
            val entityName = className.substring(className.indexOf('<') + 1, className.lastIndexOf('>'))
            RestJson.write(listOf(assembleClassToMapByName(project, entityName)))
        } else {
            RestJson.write(assembleClassToMapByName(project, className))
        }
    }.getOrDefault("")

    private fun assembleClassToMapByName(project: Project, className: String): Map<String, Any?> {
        val psiClass = findClass(project, className) ?: return LinkedHashMap()
        return assembleClassToMap(project, psiClass, DEFAULT_RECURSIVE_COUNT)
    }

    private fun assembleClassToMap(project: Project, psiClass: PsiClass, recursiveCount: Int): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for (field in psiClass.fields) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue
            val fieldType = field.type
            val typeName = fieldType.presentableText

            val baseValue = PsiClassDefaults.get(typeName)
            if (baseValue != null) {
                map[field.name] = baseValue
                continue
            }

            if (fieldType is PsiArrayType) {
                val elementValue = PsiClassDefaults.get(fieldType.componentType.presentableText)
                if (elementValue != null) map[field.name] = listOf(elementValue)
                continue
            }

            if (fieldType !is PsiClassType) continue
            val resolvedClass = runCatching { fieldType.resolve() }.getOrNull() ?: continue

            if (resolvedClass.isEnum) {
                val firstConstant = resolvedClass.fields.firstOrNull() ?: continue
                map[field.name] = firstConstant.name
                continue
            }

            // 自关联：嵌套一层（对齐原版：第二层再遇自关联即停止）
            if (recursiveCount > 0 && resolvedClass.qualifiedName != null &&
                resolvedClass.qualifiedName == psiClass.qualifiedName
            ) {
                map[field.name] = assembleClassToMap(project, resolvedClass, 0)
                continue
            }

            if (isListFieldType(fieldType)) {
                val elementType = fieldType.parameters.firstOrNull() ?: continue
                if (recursiveCount <= 0) continue
                if (elementType.presentableText == psiClass.name) {
                    map[field.name] = assembleClassToMap(project, psiClass, 0)
                    continue
                }
                val elementValue = PsiClassDefaults.get(elementType.presentableText)
                if (elementValue != null) {
                    map[field.name] = listOf(elementValue)
                    continue
                }
                if (elementType.presentableText.contains("<")) continue
                if (elementType.canonicalText.contains("<")) continue
                val elementClass = findClass(project, elementType.canonicalText) ?: continue
                // 对齐原版行为：List<POJO> 字段这里放的是对象而非数组
                map[field.name] = assembleClassToMap(project, elementClass, 0)
            }
        }
        return map
    }

    private fun isListFieldType(type: PsiClassType): Boolean {
        val resolved = runCatching { type.resolve() }.getOrNull()
        if (resolved?.qualifiedName == "java.util.List") return true
        val superTypes = runCatching { type.rawType().superTypes }.getOrNull() ?: return false
        return superTypes.any { runCatching { it.canonicalText }.getOrNull() == "java.util.List" }
    }

    private fun findClass(project: Project, qualifiedName: String): PsiClass? = runCatching {
        JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project))
    }.getOrNull()
}

/** "k : v" 文本与参数表互转（对齐原版 ToolkitUtil.textToParamMap / textToRequestParam）。 */
internal object RestParamTextSupport {

    /** 解析 RequestParams 页签文本：跳过 "//" 开头行；按首个 ":" 拆出 key/value。 */
    fun toParamMap(text: String): Map<String, String> {
        val paramMap = LinkedHashMap<String, String>()
        for (line in text.split("\n")) {
            if (line.startsWith("//") || !line.contains(":")) continue
            val parts = line.split(":", limit = 2)
            if (parts.size > 1) {
                paramMap[parts[0].trim()] = parts[1].trim()
            }
        }
        return paramMap
    }

    /** 参数表 → "k=v&k2=v2"（空表返回空串）。 */
    fun toRequestParam(paramMap: Map<String, String>): String =
        paramMap.entries.joinToString("&") { "${it.key}=${it.value}" }
}

/** Send 按钮的 HTTP 发送（零依赖：HttpURLConnection；响应为合法 JSON 时美化）。 */
internal object RestHttpClient {

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 30000

    fun request(url: String, method: String?): String {
        if (method == null) return "method is null"
        val target = completeUrl(url)
        return when (method.trim().uppercase()) {
            "GET" -> get(target)
            "POST" -> post(target)
            "PUT" -> put(target)
            "DELETE" -> delete(target)
            else -> "not supported method : $method."
        }
    }

    /** POST JSON（对齐原版 postRequestBodyWithJson 的请求头与编码）。 */
    fun postRequestBodyWithJson(url: String, json: String): String {
        val target = completeUrl(url)
        return runCatching {
            val connection = open(target, "POST")
            try {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                readResponse(connection)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { errorText(target, it) }
    }

    private fun get(target: String): String = runCatching {
        val connection = open(target, "GET")
        try {
            readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }.getOrElse { errorText(target, it) }

    /** POST 无 body（对齐原版：空 form 表单提交）。 */
    private fun post(target: String): String = runCatching {
        val connection = open(target, "POST")
        try {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            connection.outputStream.use { it.write(ByteArray(0)) }
            readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }.getOrElse { errorText(target, it) }

    private fun put(target: String): String = runCatching {
        val connection = open(target, "PUT")
        try {
            readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }.getOrElse { errorText(target, it) }

    private fun delete(target: String): String = runCatching {
        val connection = open(target, "DELETE")
        try {
            readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }.getOrElse { errorText(target, it) }

    private fun open(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        return connection
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
        return if (code >= 400) "HTTP $code\n\n$text" else RestJson.pretty(text) ?: text
    }

    private fun completeUrl(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"

    private fun errorText(url: String, throwable: Throwable): String =
        "There was an error accessing to URL: $url\n\n$throwable"
}

/** 手写 JSON 解析/美化/序列化（对齐原版 Gson pretty 输出：2 空格缩进）。 */
internal object RestJson {

    /** 合法 JSON 对象/数组 → 美化文本；否则返回 null。 */
    fun pretty(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val parsed = runCatching { Parser(trimmed).parseDocument() }.getOrNull() ?: return null
        return runCatching { write(parsed) }.getOrNull()
    }

    /** 将 Map/List/基础值序列化为 JSON（RequestBody 骨架生成用）。 */
    fun write(value: Any?): String = StringBuilder().also { writeValue(it, value, 0) }.toString()

    private class RawNumber(val raw: String)

    private class Parser(private val text: String) {

        private var position = 0

        fun parseDocument(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(position == text.length) { "Trailing characters at $position" }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            require(position < text.length) { "Unexpected end of input" }
            return when (val c = text[position]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else error("Unexpected '$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                when (val c = next()) {
                    ',' -> Unit
                    '}' -> return result
                    else -> error("Unexpected '$c' in object")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                when (val c = next()) {
                    ',' -> Unit
                    ']' -> return result
                    else -> error("Unexpected '$c' in array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                require(position < text.length) { "Unterminated string" }
                when (val c = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        require(position < text.length) { "Unterminated escape" }
                        when (val escaped = text[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                val hex = text.substring(position, position + 4)
                                builder.append(hex.toInt(16).toChar())
                                position += 4
                            }
                            else -> error("Invalid escape '\\$escaped'")
                        }
                    }
                    else -> builder.append(c)
                }
            }
        }

        private fun parseNumber(): RawNumber {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && text[position].let { it.isDigit() || it in ".eE+-" }) {
                position++
            }
            val raw = text.substring(start, position)
            require(raw.toDoubleOrNull() != null) { "Invalid number '$raw'" }
            return RawNumber(raw)
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            require(text.startsWith(literal, position)) { "Expected '$literal'" }
            position += literal.length
            return value
        }

        private fun next(): Char = text[position++]

        private fun peek(): Char? = text.getOrNull(position)

        private fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        private fun expect(c: Char) {
            require(position < text.length && text[position] == c) { "Expected '$c' at $position" }
            position++
        }

        private fun error(message: String): Nothing = throw IllegalArgumentException(message)
    }

    private fun writeValue(builder: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> builder.append("null")
            is RawNumber -> builder.append(value.raw)
            is Boolean -> builder.append(value)
            is Char -> writeString(builder, value.toString())
            is String -> writeString(builder, value)
            is Number -> builder.append(value.toString())
            is Map<*, *> -> writeObject(builder, value, indent)
            is Iterable<*> -> writeArray(builder, value, indent)
            is Array<*> -> writeArray(builder, value.asIterable(), indent)
            else -> writeString(builder, value.toString())
        }
    }

    private fun writeObject(builder: StringBuilder, map: Map<*, *>, indent: Int) {
        if (map.isEmpty()) {
            builder.append("{}")
            return
        }
        builder.append("{\n")
        val entries = map.entries.toList()
        entries.forEachIndexed { index, entry ->
            appendIndent(builder, indent + 1)
            writeString(builder, entry.key?.toString() ?: "null")
            builder.append(": ")
            writeValue(builder, entry.value, indent + 1)
            if (index < entries.size - 1) builder.append(',')
            builder.append('\n')
        }
        appendIndent(builder, indent)
        builder.append('}')
    }

    private fun writeArray(builder: StringBuilder, values: Iterable<*>, indent: Int) {
        val list = values.toList()
        if (list.isEmpty()) {
            builder.append("[]")
            return
        }
        builder.append("[\n")
        list.forEachIndexed { index, item ->
            appendIndent(builder, indent + 1)
            writeValue(builder, item, indent + 1)
            if (index < list.size - 1) builder.append(',')
            builder.append('\n')
        }
        appendIndent(builder, indent)
        builder.append(']')
    }

    private fun appendIndent(builder: StringBuilder, indent: Int) {
        repeat(indent) { builder.append("  ") }
    }

    private fun writeString(builder: StringBuilder, value: String) {
        builder.append('"')
        for (c in value) {
            when (c) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else -> if (c < ' ') {
                    builder.append("\\u").append(String.format("%04x", c.code))
                } else {
                    builder.append(c)
                }
            }
        }
        builder.append('"')
    }
}
