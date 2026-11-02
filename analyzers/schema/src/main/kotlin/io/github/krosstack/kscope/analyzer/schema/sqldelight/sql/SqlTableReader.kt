package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

import io.github.krosstack.kscope.analyzer.schema.Span

private const val IDENTIFIER = """"[^"]+"|`[^`]+`|[A-Za-z_][\w$]*"""
private const val TABLE_START = """^create\s+(?:temp(?:orary)?\s+)?table\s+(?:if\s+not\s+exists\s+)?"""
private const val TABLE_NAME = """((?:$IDENTIFIER)(?:\s*\.\s*(?:$IDENTIFIER))?)\s*\("""

private val tableHeader = Regex(TABLE_START + TABLE_NAME, RegexOption.IGNORE_CASE)
private val columnHead = Regex("""^($IDENTIFIER)\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
private val firstWord = Regex("""^[A-Za-z_]\w*""")
private val kotlinTypeClause = Regex("""\bAS\s+([A-Za-z_][\w.]*(?:<[^()]*>)?)""", RegexOption.IGNORE_CASE)
private val notNull = Regex("""\bNOT\s+NULL\b""", RegexOption.IGNORE_CASE)
private val primaryKey = Regex("""\bPRIMARY\s+KEY\b""", RegexOption.IGNORE_CASE)
private val tableConstraints = setOf("PRIMARY", "UNIQUE", "FOREIGN", "CHECK", "CONSTRAINT")

private class Chunk(val text: String, val start: Int)

/**
 * Reads the columns of a `CREATE TABLE` statement: name, SQL type, nullability and the Kotlin type of an `AS` clause.
 * SQLDelight adds that clause to SQL, so a SQL parser would reject it; the definitions of the columns are cut at their
 * top-level commas and read directly instead. A column is not null when it says so or is part of the primary key.
 */
internal object SqlTableReader {
    /** Null for a statement that is not a `CREATE TABLE` with a column list, such as `CREATE TABLE x AS SELECT`. */
    fun read(statement: SqStatement, imports: List<SqImport>): SqlTable? {
        val header = tableHeader.find(statement.text)
        val close = header?.let { SqlText.closingParenthesis(statement.text, it.range.last) }
        return if (header == null || close == null) null else tableOf(statement, imports, header, close)
    }

    private fun tableOf(statement: SqStatement, imports: List<SqImport>, header: MatchResult, close: Int): SqlTable {
        val chunks = chunksOf(statement.text, header.range.last, close)
        val keyColumns = chunks.filter { isTableConstraint(it) }.flatMap(::primaryKeyColumns).toSet()
        val columns = chunks.filterNot(::isTableConstraint).mapNotNull { columnOf(it, statement, imports, keyColumns) }
        val written = header.groupValues[1]
        val nameStart = statement.textStart + (header.groups[1]?.range?.first ?: 0)
        val nameSpan = Span(nameStart, nameStart + written.length)
        return SqlTable(unquoted(written.substringAfterLast('.').trim()), columns, statement.declaration, nameSpan)
    }

    private fun chunksOf(text: String, open: Int, close: Int): List<Chunk> {
        val boundaries = listOf(open) + SqlText.topLevelSeparators(text, open + 1, close, ',') + close
        return boundaries.zipWithNext { from, to ->
            val raw = text.substring(from + 1, to)
            Chunk(raw.trim(), from + 1 + raw.length - raw.trimStart().length)
        }.filter { it.text.isNotEmpty() }
    }

    private fun isTableConstraint(chunk: Chunk): Boolean =
        firstWord.find(chunk.text)?.value?.uppercase() in tableConstraints

    private fun primaryKeyColumns(chunk: Chunk): List<String> {
        val isPrimary = chunk.text.uppercase().startsWith("PRIMARY")
        val open = chunk.text.indexOf('(')
        val close = chunk.text.lastIndexOf(')')
        if (!isPrimary || open < 0 || close < open) return emptyList()
        return chunk.text.substring(open + 1, close).split(',').map { unquoted(it.trim().substringBefore(' ')) }
    }

    private fun columnOf(chunk: Chunk, statement: SqStatement, imports: List<SqImport>, keys: Set<String>): SqlColumn? {
        val head = columnHead.find(chunk.text) ?: return null
        val name = unquoted(head.groupValues[1])
        val rest = codeOf(head.groupValues[2])
        val sqlType = firstWord.find(rest)?.value?.takeUnless { it.uppercase() in constraintWords }.orEmpty()
        val isNullable = !notNull.containsMatchIn(rest) && !primaryKey.containsMatchIn(rest) && name !in keys
        val asType = kotlinTypeClause.find(rest)?.groupValues?.get(1)
        val type = asType?.let { ColumnType(qualified(it, imports), isNullable, isGuess = false) }
            ?: SqlTypes.kotlinTypeOf(sqlType, isNullable)
        val start = statement.textStart + chunk.start
        return SqlColumn(
            name = name,
            sqlType = sqlType,
            type = type,
            declaration = Span(start, start + chunk.text.length),
            nameSpan = Span(start, start + head.groupValues[1].length),
        )
    }

    // A Kotlin type named with the simple name of an imported class is that class.
    private fun qualified(type: String, imports: List<SqImport>): String {
        val simple = type.substringBefore('<')
        val import = imports.firstOrNull { it.name.substringAfterLast('.') == simple }?.name
        return if (import == null || '.' in simple) type else type.replaceFirst(simple, import)
    }

    private fun unquoted(name: String): String = name.removeSurrounding("\"").removeSurrounding("`")

    // A default value such as 'NOT NULL' or 'AS Long' is no constraint, so what lies between quotes is blanked.
    private fun codeOf(text: String): String {
        val code = CharArray(text.length) { ' ' }
        SqlText.codeCharacters(text).forEach { code[it.offset] = it.value }
        return String(code)
    }

    private val constraintWords = setOf(
        "NOT", "NULL", "PRIMARY", "DEFAULT", "UNIQUE", "CHECK", "REFERENCES", "COLLATE", "AS", "GENERATED",
        "CONSTRAINT",
    )
}
