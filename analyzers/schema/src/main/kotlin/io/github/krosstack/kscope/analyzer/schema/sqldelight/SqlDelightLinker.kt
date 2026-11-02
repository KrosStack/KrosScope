package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private const val NOT_FOUND = "sqldelight.table-not-found"
private const val SCOPE_UNKNOWN = "sqldelight.scope-unknown"
private const val CANDIDATES = "sqldelight.candidates"

/**
 * Answers the references of queries to tables with the table of the module that has that name. The schema of a database
 * is shared by all its `.sq` files, so the table may be defined in another file. The match is exact when one table has
 * the name and probable when several modules or files define tables of that name.
 */
internal class SqlDelightLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.sqldelight-linker", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.sqldelight),
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override val namespaces: Set<String> = setOf(SqlDelightReferences.TABLE_NAMESPACE)

    override fun link(graph: GraphReader, sink: FactSink) {
        val references = graph.symbolicReferences(SqlDelightReferences.TABLE_NAMESPACE)
        if (references.isEmpty()) return
        val lookup = TableLookup(graph)
        val facts = references.map { Fact.OfRelationship(lookup.answer(it)) }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }
}

private class TableLookup(graph: GraphReader) {
    private val moduleOfSourceSet = graph.structure().sourceSets.associate { it.id to it.module }
    private val tables: Map<Pair<ModuleId, String>, List<Symbol>> = graph
        .symbols(SymbolQuery(kindId = SqlDelightKinds.table.id, limit = Int.MAX_VALUE))
        .mapNotNull { symbol -> moduleOf(symbol)?.let { (it to symbol.name.lowercase()) to symbol } }
        .groupBy({ it.first }, { it.second })

    fun answer(reference: SymbolicReference): Relationship {
        val module = reference.scope.sourceSet?.let(moduleOfSourceSet::get)
            ?: return reference.answeredBy(Resolution.NotAnalysed(reference.key, ReasonCode(SCOPE_UNKNOWN)))
        val candidates = tables[module to reference.key.lowercase()].orEmpty().sortedBy { it.id.value }
        val inSourceSet = candidates.firstOrNull { it.scope.sourceSet == reference.scope.sourceSet }
        val best = inSourceSet ?: candidates.firstOrNull()
        return when {
            best == null -> reference.answeredBy(Resolution.Unresolved(reference.key, ReasonCode(NOT_FOUND)))

            candidates.size == 1 ->
                reference.answeredBy(Resolution.Resolved(NodeRef.OfSymbol(best.id), Certainty.EXACT))

            else -> reference.answeredBy(
                Resolution.Resolved(NodeRef.OfSymbol(best.id), Certainty.PROBABLE),
                attributes = mapOf(CANDIDATES to candidates.size.toString()),
            )
        }
    }

    private fun moduleOf(symbol: Symbol): ModuleId? =
        (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit?.let(::ModuleId)
}
