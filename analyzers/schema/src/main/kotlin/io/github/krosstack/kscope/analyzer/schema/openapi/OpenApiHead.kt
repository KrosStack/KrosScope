package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.closingQuote

private const val BYTE_ORDER_MARK = "\uFEFF"
private val versionKeys = setOf("openapi", "swagger")
private val yamlVersionKey = Regex("""(?m)^["']?(?:openapi|swagger)["']?[ \t]*:""")

/**
 * Whether the first bytes of a YAML or JSON file declare the version of an OpenAPI or Swagger document with a key of
 * the top level: a key at no indentation in YAML, a key of the outermost object in JSON.
 */
internal object OpenApiHead {
    fun declaresVersion(head: String): Boolean {
        val text = head.removePrefix(BYTE_ORDER_MARK)
        val isJson = text.trimStart().startsWith("{")
        return if (isJson) topLevelKeys(text).any { it in versionKeys } else yamlVersionKey.containsMatchIn(text)
    }

    private fun topLevelKeys(text: String): Sequence<String> = sequence {
        var depth = 0
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character == '"') {
                val close = closingQuote(text, index)
                if (close < 0) break
                if (depth == 1 && isFollowedByColon(text, close)) yield(text.substring(index + 1, close))
                index = close
            } else {
                depth += depthChange(character)
            }
            index++
        }
    }

    private fun depthChange(character: Char): Int = when (character) {
        '{', '[' -> 1
        '}', ']' -> -1
        else -> 0
    }

    private fun isFollowedByColon(text: String, close: Int): Boolean {
        val next = (close + 1 until text.length).firstOrNull { !text[it].isWhitespace() }
        return next != null && text[next] == ':'
    }
}
