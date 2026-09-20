package com.shenszq.restful.common

import java.util.Locale

object UrlPatternUtils {
    private val SCHEME_HOST = Regex("^(?:[a-zA-Z][a-zA-Z0-9+.-]*://)(?:[^/]+)")
    private val LOCALHOST_HOST = Regex("^localhost(?::\\d+)?(?=/|\$)")
    private val DOMAIN_HOST = Regex("^(?:\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+))(?::\\d+)?(?=/|\$)")

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
        val endpointLower = endpoint.lowercase(Locale.ROOT)
        val endpointRegex = toRegex(endpoint)
        for (candidate in buildCandidatePaths(input)) {
            val candidateLower = candidate.lowercase(Locale.ROOT)
            if (endpointLower == candidateLower || endpointLower.contains(candidateLower)) return true
            if (endpointLower.startsWith("$candidateLower/")) return true
            if (candidate.matches(endpointRegex)) return true
        }
        return false
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

    private fun buildCandidatePaths(input: String): List<String> {
        val candidates = linkedSetOf<String>()
        candidates.add(input)
        val trimmed = if (input.startsWith("/")) input.substring(1) else input
        if (trimmed.isEmpty()) return listOf("/")
        val segments = trimmed.split("/").toTypedArray()
        for (i in 1 until segments.size) {
            val suffix = joinNonEmpty(segments, i)
            if (suffix.isNotEmpty()) candidates.add("/$suffix")
        }
        return candidates.toList()
    }

    private fun joinNonEmpty(segments: Array<String>, start: Int): String = buildString {
        for (i in start until segments.size) {
            if (segments[i].isEmpty()) continue
            if (length > 0) append('/')
            append(segments[i])
        }
    }
}
