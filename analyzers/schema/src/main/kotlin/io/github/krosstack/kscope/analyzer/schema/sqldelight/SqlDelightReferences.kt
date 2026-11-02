package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqStatement
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlParsing
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolicReference

internal object SqlDelightReferences {
    const val TABLE_NAMESPACE = "sqldelight.table"
    const val READ = "read"
    const val WRITE = "write"
}

/**
 * The references from the queries of a `.sq` file to the tables they read and write. A table may be defined in another
 * file, so each reference is symbolic and the linker finds the table.
 */
internal class SqlDelightTableReferences(private val source: SqSource, private val site: SymbolSite) {
    fun all(): List<SymbolicReference> = source.file.queries.flatMap(::referencesOf)

    private fun referencesOf(statement: SqStatement): List<SymbolicReference> {
        val parsed = SqlParsing.parse(statement.text) ?: return emptyList()
        val from = NodeRef.OfSymbol(source.ids.queryId(source.path.baseName, checkNotNull(statement.label)))
        val reads = parsed.reads.map { it to SqlDelightReferences.READ }
        val uses = reads + parsed.writes.map { it to SqlDelightReferences.WRITE }
        return uses.distinct().flatMap { (table, subKind) ->
            sitesOf(statement, table).map { span -> reference(from, table, subKind, span) }
        }
    }

    private fun reference(from: NodeRef, table: String, subKind: String, span: Span) = SymbolicReference(
        from = from,
        kind = RelationKind.REFERENCES,
        subKind = subKind,
        namespace = SqlDelightReferences.TABLE_NAMESPACE,
        key = table,
        site = site.location(span),
        scope = site.scope,
        evidenceClass = EvidenceClass.TEXTUAL,
    )

    // Where the statement names the table after FROM, JOIN, INTO, UPDATE or a comma; the label if it cannot be found.
    private fun sitesOf(statement: SqStatement, table: String): List<Span> {
        val name = Regex.escape(table)
        val pattern = Regex(
            """(?:\bFROM|\bJOIN|\bINTO|\bUPDATE|,)\s*(?:\w+\s*\.\s*)?["`]?($name)["`]?(?![\w.])""",
            RegexOption.IGNORE_CASE,
        )
        val found = pattern.findAll(statement.text).mapNotNull { it.groups[1]?.range }
            .map { Span(statement.textStart + it.first, statement.textStart + it.last + 1) }.toList()
        return found.ifEmpty { listOf(statement.labelSpan ?: statement.declaration) }
    }
}
