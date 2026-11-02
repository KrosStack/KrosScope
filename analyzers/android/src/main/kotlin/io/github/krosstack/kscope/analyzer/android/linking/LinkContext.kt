package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.modules.ModuleGraph
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery

internal const val ALL = Int.MAX_VALUE

/** What the linkers of Android share: the source sets, the precedence among them and the modules a module sees. */
internal class LinkContext(graph: GraphReader) {
    private val structure = graph.structure()
    private val sourceSets = structure.sourceSets.associateBy { it.id }
    private val modules = ModuleGraph(
        structure.sourceSets,
        graph.relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), limit = ALL)),
    )
    private val closures = HashMap<ModuleId, List<ModuleId>>()

    val precedence = SourcePrecedence(structure.sourceSets, structure.variants)

    fun sourceSet(id: SourceSetId): SourceSet? = sourceSets[id]

    /** The modules [module] depends on, directly or not, nearest first. */
    fun dependencies(module: ModuleId): List<ModuleId> = closures.getOrPut(module) { modules.closure(module) }

    /** The module a workspace symbol is declared in, taken from its identity. */
    fun candidateOf(symbol: Symbol): LinkCandidate? {
        val identity = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity ?: return null
        return LinkCandidate(symbol.id, ModuleId(identity.unit), symbol.scope.sourceSet)
    }
}
