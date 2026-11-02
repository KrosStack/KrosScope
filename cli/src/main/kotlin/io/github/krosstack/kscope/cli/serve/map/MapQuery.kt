package io.github.krosstack.kscope.cli.serve.map

import java.net.URLDecoder

internal object MapQuery {
    /** The values of a query string, decoded; the first of a repeated name wins. Null when an escape is broken. */
    fun parse(query: String): Map<String, String>? = try {
        query.split('&').filter { it.isNotEmpty() }.map { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
        }.reversed().toMap()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun decode(text: String): String = URLDecoder.decode(text, Charsets.UTF_8)
}
