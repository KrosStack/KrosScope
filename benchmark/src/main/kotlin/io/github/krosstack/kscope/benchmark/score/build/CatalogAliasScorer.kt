package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.MappedClass
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.expectedItems
import io.github.krosstack.kscope.benchmark.score.pairKey
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.benchmark.score.unmappedMisses
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship

private val catalogKinds = setOf("gradle.catalog.library", "gradle.catalog.plugin", "gradle.catalog.bundle")

/** Version catalog aliases to the modules that use them, directly or through a convention plugin. */
internal class CatalogAliasScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val scriptModules = ScriptModules(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        val matching = Matching.of(expected, produced())
        return scoreOf("catalog_alias_to_module", gold, mapped, matching, checksEvidence = false)
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.REFERENCES }
        .flatMap(::itemsOf)
        .distinctBy { it.key }

    private fun itemsOf(relationship: Relationship): List<Item> {
        val alias = index.resolvedTarget(relationship) as? NodeRef.OfSymbol
        val isCatalogEntry = alias != null && index.symbolsById[alias.id]?.kind?.id in catalogKinds
        val script = (relationship.from as? NodeRef.OfArtifact)?.let { index.artifactsByPath[it.ref.path.value] }
        return if (alias == null || !isCatalogEntry || script == null) {
            emptyList()
        } else {
            scriptModules.modulesOf(script).map { itemOf(alias, it) }
        }
    }

    private fun itemOf(alias: NodeRef, module: Module) =
        Item(pairKey(alias, NodeRef.OfModule(module.id)), "${alias.key()} -> ${module.id.value}")
}
