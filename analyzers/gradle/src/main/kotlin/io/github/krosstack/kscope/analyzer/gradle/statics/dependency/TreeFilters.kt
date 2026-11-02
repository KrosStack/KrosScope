package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

/** The `include` and `exclude` patterns of a `fileTree(...)`, as written. */
internal class TreePatterns(val include: List<String>, val exclude: List<String>)

private const val LITERAL = """"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'"""
private const val LITERALS = """(?:$LITERAL)(?:\s*,\s*(?:$LITERAL))*"""
private const val LIST = """(?:$LITERALS\s*,?\s*)?"""
private val literal = Regex(LITERAL)
private val keyedValue = Regex("""$LITERAL|\w+\s*\(\s*$LIST\)|\[\s*$LIST]""")
private val statement = Regex(
    """(?<![\w.])(include|exclude)(?:\s*\(\s*($LITERALS)\s*\)|\s+($LITERALS))""",
)

/**
 * Reads the patterns that select files of a `fileTree(...)`: the `include` and `exclude` keys of its map form and the
 * `include(...)` and `exclude(...)` calls of its block. Only string literals are read; any other pattern, or any
 * other statement in the block, makes the selection unreadable.
 */
internal object TreeFilters {
    private const val INCLUDE = "include"
    private const val EXCLUDE = "exclude"

    fun of(arguments: String, block: String): TreePatterns? {
        val keyed = keyedPatterns(arguments)
        val called = calledPatterns(block)
        return if (keyed == null || called == null) {
            null
        } else {
            TreePatterns(keyed.include + called.include, keyed.exclude + called.exclude)
        }
    }

    private fun keyedPatterns(arguments: String): TreePatterns? {
        val include = keyedValues(arguments, INCLUDE)
        val exclude = keyedValues(arguments, EXCLUDE)
        return if (include == null || exclude == null) null else TreePatterns(include, exclude)
    }

    private fun keyedValues(arguments: String, key: String): List<String>? {
        val start = Regex("""(?<![\w.])["']?$key["']?\s*(?:to|:|=)\s*""")
        val values = start.findAll(arguments).map { keyedValue.matchAt(arguments, it.range.last + 1)?.value }.toList()
        return if (values.any { it == null }) null else literalsOf(values.joinToString(","))
    }

    private fun calledPatterns(block: String): TreePatterns? {
        if (!block.replace(statement, "").all { it.isWhitespace() || it == ';' }) return null
        val calls = statement.findAll(block).map { call ->
            call.groupValues[1] to literalsOf(call.groupValues[2] + call.groupValues[3])
        }.toList()
        return if (calls.any { it.second == null }) {
            null
        } else {
            TreePatterns(patternsOf(calls, INCLUDE), patternsOf(calls, EXCLUDE))
        }
    }

    private fun patternsOf(calls: List<Pair<String, List<String>?>>, name: String): List<String> =
        calls.filter { it.first == name }.flatMap { it.second.orEmpty() }

    private fun literalsOf(text: String): List<String>? {
        val patterns = literal.findAll(text).map { it.value.substring(1, it.value.length - 1) }.toList()
        val readable = patterns.none { it.isBlank() || '$' in it || '\\' in it }
        return patterns.takeIf { readable }
    }
}
