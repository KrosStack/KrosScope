package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

internal enum class BindUse {
    /** One value compared with or assigned to a column. */
    VALUE,

    /** A collection of values that a column is `IN`. */
    COLLECTION,

    /** A row count or offset of `LIMIT`. */
    COUNT,

    /** A whole row, as in `INSERT INTO t VALUES ?`. */
    ROW,
}

/** Where a bind argument is used: [column] is the column it is compared with or assigned to, if there is one. */
internal class BindContext(val use: BindUse, val qualifier: String?, val column: String?)

/**
 * A bind argument of a statement, `?`, `?1` or `:name`. A name used twice is one argument; each `?` is its own.
 * [name] is set for `:name` and null for the positional forms.
 */
internal class Bind(val key: String, val name: String?, val offset: Int, val context: BindContext?)

private const val NAME = """[A-Za-z_]\w*|"[^"]+"|`[^`]+`"""
private const val OPERATOR = """=|==|!=|<>|<=|>=|<|>|\bNOT\s+LIKE\b|\bLIKE\b|\bGLOB\b|\bMATCH\b|\bIS\s+NOT\b|\bIS\b"""
private const val OPTIONAL_QUALIFIER = """(?:($NAME)\s*\.\s*)?"""

private val comparison = Regex("""$OPTIONAL_QUALIFIER($NAME)\s*(?:$OPERATOR)\s*$""", RegexOption.IGNORE_CASE)
private val membership = Regex("""$OPTIONAL_QUALIFIER($NAME)\s+(?:NOT\s+)?IN\s*\(?\s*$""", RegexOption.IGNORE_CASE)
private val betweenStart = Regex("""$OPTIONAL_QUALIFIER($NAME)\s+(?:NOT\s+)?BETWEEN\s*$""", RegexOption.IGNORE_CASE)
private val betweenEnd =
    Regex("""$OPTIONAL_QUALIFIER($NAME)\s+(?:NOT\s+)?BETWEEN\s+\S+\s+AND\s*$""", RegexOption.IGNORE_CASE)
private val limit = Regex("""\b(?:LIMIT|OFFSET)\s*$|\bLIMIT\s+\S+\s*,\s*$""", RegexOption.IGNORE_CASE)
private val fullRow = Regex("""\bVALUES\s*$""", RegexOption.IGNORE_CASE)
private val namedStart = Regex("""[A-Za-z_]""")

/** Finds the bind arguments of a statement and what each one is compared with, from the text next to it. */
internal object SqlBinds {
    fun of(text: String): List<Bind> {
        val found = mutableListOf<Bind>()
        for ((offset, character) in SqlText.codeCharacters(text)) {
            val key = keyAt(text, offset, character, found)
            if (key != null && found.none { it.key == key }) found += bindOf(text, key, offset)
        }
        return found
    }

    // An anonymous `?` takes the number after the largest one given so far, as SQLite numbers its arguments.
    private fun keyAt(text: String, offset: Int, character: Char, before: List<Bind>): String? = when {
        character == '?' -> "?${numberAt(text, offset) ?: (largestNumber(before) + 1)}"
        character == ':' && isNamedBind(text, offset) -> nameAt(text, offset + 1)
        else -> null
    }

    private fun largestNumber(binds: List<Bind>): Int =
        binds.maxOfOrNull { it.key.removePrefix("?").toIntOrNull() ?: 0 } ?: 0

    private fun nameAt(text: String, start: Int): String =
        text.substring(start).takeWhile { it.isLetterOrDigit() || it == '_' }

    private fun bindOf(text: String, key: String, offset: Int): Bind =
        Bind(key, key.takeUnless { it.startsWith("?") }, offset, contextOf(text.substring(0, offset)))

    private fun numberAt(text: String, offset: Int): Int? =
        text.substring(offset + 1).takeWhile { it.isDigit() }.toIntOrNull()

    private fun isNamedBind(text: String, colon: Int): Boolean {
        val next = text.getOrNull(colon + 1)?.toString().orEmpty()
        val before = text.getOrNull(colon - 1)
        val afterIdentifier = before != null && (before.isLetterOrDigit() || before == '_')
        return namedStart.matches(next) && before != ':' && !afterIdentifier
    }

    private fun contextOf(before: String): BindContext? {
        val trimmed = before.trimEnd()
        return when {
            fullRow.containsMatchIn(before) -> BindContext(BindUse.ROW, null, null)
            limit.containsMatchIn(before) -> BindContext(BindUse.COUNT, null, null)
            else -> columnContext(before, trimmed)
        }
    }

    private fun columnContext(before: String, trimmed: String): BindContext? {
        val candidates = listOf(
            betweenEnd to BindUse.VALUE,
            betweenStart to BindUse.VALUE,
            membership to BindUse.COLLECTION,
            comparison to BindUse.VALUE,
        )
        val (match, use) = candidates.firstNotNullOfOrNull { (pattern, use) ->
            pattern.find(before)?.let { it to use }
        } ?: return null
        val inList = use == BindUse.COLLECTION && trimmed.endsWith("(")
        val qualifier = match.groupValues[1].ifEmpty { null }?.unquoted()
        return BindContext(if (inList) BindUse.VALUE else use, qualifier, match.groupValues[2].unquoted())
    }
}
