package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.Span

/**
 * A route declared with the routing DSL: the [method] in upper case, the full [path] with the prefixes of the blocks
 * that enclose it, the span of the whole call with its block and the span of the name of the call.
 */
internal data class KtorRoute(val method: String, val path: String, val declaration: Span, val nameSpan: Span) {
    val name: String get() = "$method $path"
}
