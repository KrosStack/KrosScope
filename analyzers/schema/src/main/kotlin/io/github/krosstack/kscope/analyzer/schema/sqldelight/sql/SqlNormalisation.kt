package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

private val conflictClause = Regex(
    """^(\s*(?:INSERT|UPDATE))\s+OR\s+(?:REPLACE|IGNORE|ABORT|FAIL|ROLLBACK)\b""",
    RegexOption.IGNORE_CASE,
)
private val replaceInto = Regex("""^(\s*)REPLACE\s+INTO\b""", RegexOption.IGNORE_CASE)
private val wholeRow = Regex("""\bVALUES\s+(\?\d*|:\w+)""", RegexOption.IGNORE_CASE)
private val sqliteOperators = Regex("""\b(?:GLOB|MATCH)\b""", RegexOption.IGNORE_CASE)
private val indexHint = Regex("""\b(?:NOT\s+INDEXED|INDEXED\s+BY\s+\w+)""", RegexOption.IGNORE_CASE)

/**
 * Rewrites what the SQL parser does not know of SQLite and SQLDelight into what it knows. Only code is rewritten: the
 * names between quotes and the strings of a statement stay as they are.
 */
internal object SqlNormalisation {
    fun of(text: String): String {
        val withoutConflict = text.replace(conflictClause, "$1").replace(replaceInto, "$1INSERT INTO")
        return SqlText.mapCode(withoutConflict) { code ->
            code.replace(wholeRow, "VALUES ($1)").replace(sqliteOperators, "LIKE").replace(indexHint, "")
        }
    }
}
