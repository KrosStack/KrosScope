package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.schema.lexing.afterBalanced

private const val ROUTE = "route"
private const val TRIPLE_QUOTE = "\"\"\""
private const val FIRST_ARGUMENT = 2
private const val AFTER_FIRST_ARGUMENT = 3
private val dslNames = setOf("get", "post", "put", "delete", "patch", "head", "options", ROUTE)

/** A call of the routing DSL with a block: the index of its name, the path it gives and the index of its `{`. */
private class DslCall(val nameIndex: Int, val name: String, val path: String?, val open: Int)

/**
 * Finds the routes in the tokens of a Kotlin file by the shape of the calls: `get("/tasks") { ... }` and the other
 * methods with a literal path, the same with no path inside a `route("/prefix") { ... }` block, and the blocks of
 * `route` that give the prefix of what they enclose. A call with a receiver (`client.get("/x") { ... }`), a typed
 * call (`get<Resource> { ... }`) and a call whose path is built are no routes; a `route` block with a path that is
 * built hides what it encloses, as the path of those routes is not known. This is a lexical match, not a resolver.
 */
internal class KtorRouteBlocks(private val tokens: List<SourceToken>) {
    private val closers = closingBraces()

    fun routes(): List<KtorRoute> = ArrayList<KtorRoute>().also { collect(0, tokens.size, null, it) }

    private fun collect(from: Int, to: Int, prefix: List<String>?, routes: MutableList<KtorRoute>) {
        var index = from
        while (index < to) {
            val call = callAt(index, prefix != null)
            index = if (call == null) index + 1 else take(call, prefix, routes)
        }
    }

    private fun take(call: DslCall, prefix: List<String>?, routes: MutableList<KtorRoute>): Int {
        val close = closers[call.open] ?: tokens.size
        if (call.name == ROUTE) {
            call.path?.let { collect(call.open + 1, close, prefix.orEmpty() + it, routes) }
        } else {
            val name = tokens[call.nameIndex]
            val last = tokens[minOf(close, tokens.size - 1)]
            val path = joinRoutePath(prefix.orEmpty() + call.path.orEmpty())
            routes += KtorRoute(call.name.uppercase(), path, Span(name.span.start, last.span.end), name.span)
        }
        return close + 1
    }

    private fun callAt(index: Int, isInRoute: Boolean): DslCall? {
        val name = tokens[index]
        val next = tokens.getOrNull(index + 1)
        return when {
            name.kind != TokenKind.IDENTIFIER || name.text !in dslNames || isQualified(index) -> null
            next == null -> null
            next.isPunctuation("(") -> argumentCall(index)
            else -> blockCall(index, isInRoute).takeIf { next.isPunctuation("{") }
        }
    }

    private fun blockCall(index: Int, isInRoute: Boolean): DslCall? =
        DslCall(index, tokens[index].text, "", index + 1).takeIf { isInRoute && tokens[index].text != ROUTE }

    private fun isQualified(index: Int): Boolean = index > 0 && tokens[index - 1].isPunctuation(".")

    private fun argumentCall(index: Int): DslCall? {
        val name = tokens[index].text
        val close = tokens.afterBalanced(index + 1, "(", ")") - 1
        val hasBlock = tokens.getOrNull(close)?.isPunctuation(")") == true &&
            tokens.getOrNull(close + 1)?.isPunctuation("{") == true
        val literal = tokens.getOrNull(index + FIRST_ARGUMENT)?.takeIf { it.kind == TokenKind.STRING }
        val path = literal?.let { plainPath(it.text) }
        return when {
            !hasBlock -> null
            name == ROUTE -> DslCall(index, name, path.takeIf { endsArgument(index + AFTER_FIRST_ARGUMENT) }, close + 1)
            close == index + AFTER_FIRST_ARGUMENT && path != null -> DslCall(index, name, path, close + 1)
            else -> null
        }
    }

    private fun endsArgument(index: Int): Boolean =
        tokens.getOrNull(index)?.let { it.isPunctuation(",") || it.isPunctuation(")") } == true

    private fun plainPath(literal: String): String? {
        val body = literal.removeSurrounding("\"")
        val isQuoted = literal.length >= 2 && literal.startsWith('"') && literal.endsWith('"')
        val isPlain = isQuoted && !literal.startsWith(TRIPLE_QUOTE) && body.none { it == '$' || it == '\\' }
        return body.takeIf { isPlain }
    }

    private fun closingBraces(): Map<Int, Int> {
        val closers = HashMap<Int, Int>()
        val open = ArrayDeque<Int>()
        tokens.forEachIndexed { index, token ->
            if (token.isPunctuation("{")) open.addLast(index)
            if (token.isPunctuation("}")) open.removeLastOrNull()?.let { closers[it] = index }
        }
        return closers
    }
}
