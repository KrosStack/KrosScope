package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

import io.github.krosstack.kscope.analyzer.schema.Span

internal enum class SqKind { CREATE_TABLE, SELECT, INSERT, UPDATE, DELETE, OTHER }

internal class SqImport(val name: String, val span: Span)

/**
 * One statement of a `.sq` file. [text] is the statement without its label, its closing semicolon and its comments,
 * and [textStart] is where it starts in the file. A statement with a [label] is a named query.
 */
internal class SqStatement(
    val label: String?,
    val labelSpan: Span?,
    val declaration: Span,
    val textStart: Int,
    val text: String,
) {
    val kind: SqKind = kindOf(text)

    private companion object {
        private val createTable = Regex("""^create\s+(?:temp(?:orary)?\s+)?table\b""", RegexOption.IGNORE_CASE)
        private val keyword = Regex("""^\w+""")

        fun kindOf(text: String): SqKind = when {
            createTable.containsMatchIn(text) -> SqKind.CREATE_TABLE

            else -> when (keyword.find(text)?.value?.uppercase()) {
                "SELECT", "WITH", "VALUES" -> SqKind.SELECT
                "INSERT", "REPLACE" -> SqKind.INSERT
                "UPDATE" -> SqKind.UPDATE
                "DELETE" -> SqKind.DELETE
                else -> SqKind.OTHER
            }
        }
    }
}

internal class SqFile(val imports: List<SqImport>, val statements: List<SqStatement>) {
    val queries: List<SqStatement> get() = statements.filter { it.label != null && it.kind != SqKind.OTHER }
    val tables: List<SqStatement> get() = statements.filter { it.kind == SqKind.CREATE_TABLE }
}

private class Segment(val span: Span, val text: String)

/**
 * Splits a `.sq` file into its `import` lines, its unlabelled statements such as table definitions and its labelled
 * statements, which are the queries. A statement ends at a semicolon outside strings, quoted names and comments, and a
 * trigger at the semicolon after its `END`.
 */
internal object SqFileReader {
    private val label = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*:(?!:)""")
    private val importStatement = Regex("""^import\s+([^\s;]+)""", RegexOption.IGNORE_CASE)
    private val triggerStart =
        Regex("""^(?:\w+\s*:\s*)?create\s+(?:temp(?:orary)?\s+)?trigger\b""", RegexOption.IGNORE_CASE)
    private val endsWithEnd = Regex("""\bend\s*$""", RegexOption.IGNORE_CASE)

    fun read(text: String): SqFile {
        val clean = SqlText.withoutComments(text)
        val segments = segmentsOf(clean).mapNotNull { trimmed(clean, it) }
        val imports = segments.mapNotNull(::importOf)
        val statements = segments.filter { importOf(it) == null }.map(::statementOf)
        return SqFile(imports, statements)
    }

    private fun segmentsOf(clean: String): List<Span> {
        val semicolons = SqlText.codeCharacters(clean).filter { it.value == ';' }.map { it.offset }
        val spans = mutableListOf<Span>()
        var start = 0
        for (semicolon in semicolons) {
            val head = clean.substring(start, semicolon)
            val isOpenTrigger = triggerStart.containsMatchIn(head.trimStart()) && !endsWithEnd.containsMatchIn(head)
            if (!isOpenTrigger) {
                spans += Span(start, semicolon + 1)
                start = semicolon + 1
            }
        }
        if (clean.substring(start).isNotBlank()) spans += Span(start, clean.length)
        return spans
    }

    private fun trimmed(clean: String, span: Span): Segment? {
        val raw = clean.substring(span.start, span.end)
        val leading = raw.length - raw.trimStart().length
        val body = raw.trim().removeSuffix(";").trimEnd()
        return body.takeIf { it.isNotEmpty() }?.let { Segment(Span(span.start + leading, span.end), it) }
    }

    private fun importOf(segment: Segment): SqImport? =
        importStatement.find(segment.text)?.let { SqImport(it.groupValues[1], segment.span) }

    private fun statementOf(segment: Segment): SqStatement {
        val found = label.find(segment.text)
        val text = (found?.let { segment.text.substring(it.value.length) } ?: segment.text).trimStart()
        val labelSpan = found?.let { Span(segment.span.start, segment.span.start + it.groupValues[1].length) }
        val textStart = segment.span.start + segment.text.length - text.length
        return SqStatement(found?.groupValues?.get(1), labelSpan, segment.span, textStart, text)
    }
}
