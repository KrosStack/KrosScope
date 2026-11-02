package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.graph.label
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship

private class ProducedEdge(val relationship: Relationship, val target: NodeRef, val item: Item)

/**
 * The edges the graph holds from the sites of a file to workspace nodes, as items to compare with gold.
 *
 * A supertype initializer with arguments, as in `object : Base()`, is a call of the constructor and the supertype of
 * the object at once. The graph states both at one site and gold states the supertype once, so the calls and extends
 * edges of one file, line and class (a constructor counts as its class) are pairwise one item, the extends edge.
 */
internal class ProducedEdges(private val index: GraphIndex) {
    fun of(file: String): List<Item> {
        val edges = index.relationshipsBySitePath[file].orEmpty().mapNotNull(::edgeOf)
        return edges.groupBy(::supertypeSite).values.flatMap(::collapseSupertypeCalls).map { it.item }
    }

    private fun edgeOf(relationship: Relationship): ProducedEdge? {
        val target = index.resolvedTarget(relationship)?.takeIf(index::isWorkspaceTarget)
            ?.takeUnless(index::isFieldHolder)
        val site = relationship.site?.range
        return if (target == null || site == null) {
            null
        } else {
            ProducedEdge(
                relationship,
                target,
                Item("${site.startLine}|${target.key()}", labelOf(relationship), kindOf(relationship)),
            )
        }
    }

    private fun supertypeSite(edge: ProducedEdge): String =
        "${edge.relationship.site?.range?.startLine}|${index.ownerOf(edge.target).key()}"

    private fun collapseSupertypeCalls(sameSite: List<ProducedEdge>): List<ProducedEdge> {
        val calls = sameSite.filter { it.relationship.kind == RelationKind.CALLS }
        val extends = sameSite.filter { it.relationship.kind == RelationKind.EXTENDS }
        val pairCount = minOf(calls.size, extends.size)
        return sameSite.filterNot { it in calls.take(pairCount) }
    }

    private fun labelOf(relationship: Relationship): String {
        val site = relationship.site?.range
        val target = index.resolvedTarget(relationship)?.label().orEmpty()
        return "${kindOf(relationship)} -> $target @${site?.startLine}:${site?.startColumn}"
    }

    private fun kindOf(relationship: Relationship): String =
        listOfNotNull(relationship.kind.name.lowercase().replace('_', '-'), relationship.subKind).joinToString("/")
}
