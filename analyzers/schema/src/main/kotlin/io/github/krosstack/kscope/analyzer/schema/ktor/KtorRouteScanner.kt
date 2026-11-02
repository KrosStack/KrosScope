package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.lexing.KotlinTokenizer
import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken

private const val ROUTING_PACKAGE = "io.ktor.server.routing"
private val routingPackageTokens = listOf("io", ".", "ktor", ".", "server", ".", "routing")

/**
 * Finds the routes that a Kotlin file declares with the routing DSL of Ktor. Only a file that imports from
 * `io.ktor.server.routing` is read, and what is in a comment or a string is never taken for code, because the tokens
 * come from [KotlinTokenizer].
 */
internal class KtorRouteScanner(private val text: String) {
    fun scan(): List<KtorRoute> {
        if (ROUTING_PACKAGE !in text) return emptyList()
        val tokens = KotlinTokenizer(text).tokens()
        return if (importsRouting(tokens)) KtorRouteBlocks(tokens).routes() else emptyList()
    }

    private fun importsRouting(tokens: List<SourceToken>): Boolean =
        tokens.indices.any { tokens[it].isIdentifier("import") && continuesWithPackage(tokens, it + 1) }

    private fun continuesWithPackage(tokens: List<SourceToken>, from: Int): Boolean =
        routingPackageTokens.indices.all { tokens.getOrNull(from + it)?.text == routingPackageTokens[it] }
}
