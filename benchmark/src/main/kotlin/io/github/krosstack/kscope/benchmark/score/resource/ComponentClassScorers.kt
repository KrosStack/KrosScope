package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.MappedClass
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.benchmark.score.expectedItems
import io.github.krosstack.kscope.benchmark.score.pairKey
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.benchmark.score.unmappedMisses
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory

private const val MANIFEST_KIND_PREFIX = "android.manifest."
private val layoutKinds = setOf("android.resource.layout", "android.resource.navigation")

private fun GraphIndex.symbolOf(node: NodeRef?): Symbol? = (node as? NodeRef.OfSymbol)?.let { symbolsById[it.id] }

/**
 * Every component of a manifest to the class that implements it. The graph states it from the component symbol to the
 * class, so both ends are symbols.
 */
internal class ManifestComponentScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        return scoreOf(NAME, gold, mapped, Matching.of(expected, produced()))
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.BINDS }
        .mapNotNull { relationship ->
            val isComponent = index.symbolOf(relationship.from)?.kind?.id?.startsWith(MANIFEST_KIND_PREFIX) == true
            val target = index.resolvedTarget(relationship)
            if (isComponent && target != null) itemOf(relationship, target) else null
        }

    private fun itemOf(relationship: Relationship, target: NodeRef) =
        Item(pairKey(relationship.from, target), "binds", evidenceOf(relationship))

    private companion object {
        const val NAME = "manifest_component_to_class"
    }
}

/**
 * Every class that a layout or a navigation graph names to the place that names it. A relationship is told from the
 * others of the same two ends by the line of its site.
 */
internal class LayoutClassReferenceScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { "${pairKey(it.from, it.to)}|${it.gold.from.line}" }
        return scoreOf(NAME, gold, mapped, Matching.of(expected + mapped.unmappedMisses(), produced()))
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.REFERENCES }
        .mapNotNull { relationship ->
            val target = index.resolvedTarget(relationship)
            val line = relationship.site?.range?.startLine
            if (isClassReference(relationship, target) && target != null && line != null) {
                itemOf(relationship, target, line)
            } else {
                null
            }
        }

    private fun isClassReference(relationship: Relationship, target: NodeRef?): Boolean {
        val isFromLayout = index.symbolOf(relationship.from)?.kind?.id in layoutKinds
        return isFromLayout && index.symbolOf(target)?.kind?.category == SymbolCategory.TYPE
    }

    private fun itemOf(relationship: Relationship, target: NodeRef, line: Int) =
        Item("${pairKey(relationship.from, target)}|$line", "references @$line", evidenceOf(relationship))

    private companion object {
        const val NAME = "layout_class_reference"
    }
}
