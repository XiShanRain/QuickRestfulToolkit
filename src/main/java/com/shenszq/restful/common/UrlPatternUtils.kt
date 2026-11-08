package com.xishanrain.restful.common

import java.util.Locale

object UrlPatternUtils {
    private val SCHEME_HOST = Regex("^(?:[a-zA-Z][a-zA-Z0-9+.-]*://)(?:[^/]+)")
    private val LOCALHOST_HOST = Regex("^localhost(?::\\d+)?(?=/|\$)")
    private val DOMAIN_HOST = Regex("^(?:\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+))(?::\\d+)?(?=/|\$)")
    private val NEVER_MATCH = Regex("(?!)")

    fun normalizeUserInput(value: String?): String {
        if (value == null) return ""
        var normalized = value.trim().replace('\\', '/')
        normalized = stripQuotes(normalized)
        normalized = stripQueryAndFragment(normalized)
        normalized = stripHost(normalized)
        normalized = normalized.replace(Regex("/+"), "/")
        if (normalized.isEmpty()) return "/"
        if (!normalized.startsWith("/")) normalized = "/$normalized"
        return normalized
    }

    fun matches(endpointPath: String, userPattern: String): Boolean {
        val endpoint = normalizeUserInput(endpointPath)
        val input = normalizeUserInput(userPattern)
        if (input == "/") return true
        val epLower = endpoint.lowercase(Locale.ROOT)
        val inputLower = input.lowercase(Locale.ROOT)
        // 根路径端点 "/"（如 @RequestMapping("/")）只在空/根查询（上面的 matchAll）时展示，
        // 否则任意查询都以 '/' 起头并包含 '/'，反向包含会把它误命中。
        if (epLower == "/") return false
        // 双向“分段连续包含”：
        //  - endpoint 含 input（endpoint 前/后有额外分段，含末段前缀式输入如 /api/us 命中 /api/users/1）
        //  - input 含 endpoint（input 带多余前缀，如 context-root：/mftcc/label/x 命中 /label/x）
        // 二者都以 '/' 起头，天然保证按段边界对齐，不会把多段查询拆成裸末段而误命中。
        if (epLower == inputLower || epLower.contains(inputLower)) return true
        if (inputLower.contains(epLower)) return true
        // 模板端点（含 {var} / {var:regex}）：用编译好的正则匹配原始输入
        val regex = try {
            toRegex(endpoint)
        } catch (t: Throwable) {
            NEVER_MATCH
        }
        return input.matches(regex)
    }

    /**
     * 端点路径的预计算匹配键：归一化原文 + 小写 + 预编译正则。
     * 列表加载时对每个条目计算一次，之后每轮按键匹配只做纯比较，避免重复归一化/编译正则。
     */
    class EndpointKey internal constructor(
        val normalized: String,
        val normalizedLower: String,
        val regex: Regex
    )

    /**
     * 把用户查询预编译成可复用的匹配器：归一化只算一次。
     * 匹配采用双向分段连续包含（endpoint 含 query 或 query 含 endpoint）+ 模板正则，
     * 热路径只做廉价的字符串包含比较，不再拆后缀、不再编译正则。
     */
    class QueryMatcher internal constructor(
        private val matchAll: Boolean,
        private val patternLower: String,
        private val patternRaw: String
    ) {
        fun matches(key: EndpointKey): Boolean {
            if (matchAll) return true
            val epLower = key.normalizedLower
            // 根路径端点 "/" 不参与具体查询匹配（见 matches(...) 中的同规则说明）
            if (epLower == "/") return false
            if (epLower == patternLower || epLower.contains(patternLower)) return true
            if (patternLower.contains(epLower)) return true
            return patternRaw.matches(key.regex)
        }
    }

    fun buildEndpointKey(endpointPath: String): EndpointKey {
        val normalized = normalizeUserInput(endpointPath)
        val regex = try {
            toRegex(normalized)
        } catch (t: Throwable) {
            // 非法 {var:type} 之类导致的正则编译失败：退化为“正则永不匹配”，仍保留纯字符串比较能力
            NEVER_MATCH
        }
        return EndpointKey(normalized, normalized.lowercase(Locale.ROOT), regex)
    }

    fun compileQuery(userPattern: String): QueryMatcher {
        val input = normalizeUserInput(userPattern)
        if (input == "/") return QueryMatcher(true, "/", input)
        return QueryMatcher(false, input.lowercase(Locale.ROOT), input)
    }

    private fun stripQuotes(value: String): String =
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) value.substring(1, value.length - 1) else value

    private fun stripQueryAndFragment(value: String): String {
        val qi = value.indexOf('?')
        val hi = value.indexOf('#')
        val cut = when {
            qi >= 0 && hi >= 0 -> minOf(qi, hi)
            qi >= 0 -> qi
            hi >= 0 -> hi
            else -> -1
        }
        return if (cut >= 0) value.substring(0, cut) else value
    }

    private fun stripHost(value: String): String {
        val m1 = SCHEME_HOST.replaceFirst(value, "")
        if (m1 != value) return if (m1.isEmpty()) "/" else m1
        val m2 = LOCALHOST_HOST.replaceFirst(value, "")
        if (m2 != value) return if (m2.isEmpty()) "/" else m2
        val m3 = DOMAIN_HOST.replaceFirst(value, "")
        if (m3 != value) return if (m3.isEmpty()) "/" else m3
        return value
    }

    private fun toRegex(endpoint: String): Regex {
        val regex = StringBuilder("^")
        var i = 0
        while (i < endpoint.length) {
            val c = endpoint[i]
            if (c == '{') {
                val end = endpoint.indexOf('}', i)
                if (end < 0) { regex.append("\\{"); i++; continue }
                val variable = endpoint.substring(i + 1, end)
                val sep = variable.indexOf(':')
                if (sep >= 0 && sep + 1 < variable.length) {
                    regex.append('(').append(variable.substring(sep + 1)).append(')')
                } else {
                    regex.append("[^/]+")
                }
                i = end
                i++
                continue
            }
            if ("\\.[]{}()+-*?^$|".indexOf(c) >= 0) regex.append('\\')
            regex.append(c)
            i++
        }
        regex.append('$')
        return Regex(regex.toString())
    }
}
