package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId

/** The modules a module depends on, directly and transitively, read from the `DEPENDS_ON` edges of the build model. */
internal class ModuleDependencies(sourceSets: List<SourceSet>, relationships: List<Relationship>) {
    private val moduleOfSourceSet: Map<SourceSetId, ModuleId> = sourceSets.associate { it.id to it.module }
    private val direct: Map<ModuleId, List<ModuleId>> = relationships
        .filter { it.kind == RelationKind.DEPENDS_ON }
        .mapNotNull(::edgeOf)
        .filter { (from, to) -> from != to }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, targets) -> targets.distinct().sortedBy { it.value } }

    /** The modules [module] depends on transitively, nearest first, modules at one distance ordered by id. */
    fun closure(module: ModuleId): List<ModuleId> {
        val reached = linkedSetOf(module)
        var frontier = listOf(module)
        val ordered = mutableListOf<ModuleId>()
        while (frontier.isNotEmpty()) {
            frontier = frontier.flatMap { direct[it].orEmpty() }.distinct().filter(reached::add).sortedBy { it.value }
            ordered += frontier
        }
        return ordered
    }

    private fun edgeOf(edge: Relationship): Pair<ModuleId, ModuleId>? {
        val from = moduleOf(edge.from)
        val to = (edge.resolution as? Resolution.Resolved)?.target?.let(::moduleOf)
        return if (from != null && to != null) from to to else null
    }

    private fun moduleOf(node: NodeRef): ModuleId? = when (node) {
        is NodeRef.OfModule -> node.id
        is NodeRef.OfSourceSet -> moduleOfSourceSet[node.id]
        is NodeRef.OfSymbol, is NodeRef.OfArtifact, is NodeRef.OfExternal -> null
    }
}
