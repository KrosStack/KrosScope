package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.AddressResolution
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.MappedClass
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin

private const val BUILD_CONFIG_GENERATOR = "android.buildconfig"
private val codeTypes = setOf("kotlin.source", "java.source")

// The gold lines of a consumer file, as items keyed by the node the source of truth maps to, the file and the line.
private fun expectedConsumerItems(relationship: GoldRelationship, from: AddressResolution): List<Item> {
    val lines = relationship.to.lines.ifEmpty { listOfNotNull(relationship.to.line) }
    val file = relationship.to.file.orEmpty()
    val origin = (from as? AddressResolution.Found)?.node?.key() ?: "unmapped|${relationship.id}"
    return lines.map { Item("$origin|$file|$it", "${relationship.id} $file:$it", relationship.expectedEvidence) }
}

private fun unmappedEnds(gold: List<GoldRelationship>, resolver: GoldAddressResolver): MappedClass {
    val unmapped = gold.mapNotNull { relationship ->
        val from = resolver.resolve(relationship.from) as? AddressResolution.Unmapped
        from?.let { "${relationship.id}: ${it.describe()}" }
    }
    return MappedClass(emptyList(), unmapped)
}

/**
 * Every layout to each source file that names its binding class. The binding class is a surface that view binding makes
 * from the layout; the evidence is that of the hop from the layout to it.
 */
internal class ViewBindingScorer(index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val consumers = ResourceConsumers(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val expected = gold.flatMap { expectedConsumerItems(it, resolver.resolve(it.from)) }
        val produced = consumers.bindingEdges().map { edge ->
            val key = "${NodeRef.OfSymbol(edge.resource.id).key()}|${edge.path}|${edge.line}"
            Item(key, "${edge.resource.name} <- ${edge.path}:${edge.line}", edge.evidence)
        }
        return scoreOf(NAME, gold, unmappedEnds(gold, resolver), Matching.of(expected, produced))
    }

    private companion object {
        const val NAME = "viewbinding_layout_to_consumer"
    }
}

/**
 * Every field of `BuildConfig`, standard or declared by a build script, to each source file line that reads it. The
 * evidence is that of the hop from the build script to the field.
 */
internal class BuildConfigScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val scriptEdges: Map<SymbolId, Relationship> = index.relationships
        .filter { it.kind == RelationKind.GENERATES && it.from is NodeRef.OfArtifact }
        .mapNotNull { edge -> (index.resolvedTarget(edge) as? NodeRef.OfSymbol)?.let { it.id to edge } }
        .toMap()

    fun score(gold: List<GoldRelationship>): ClassScore {
        val expected = gold.flatMap { expectedConsumerItems(it, resolver.resolve(it.from)) }
        return scoreOf(NAME, gold, unmappedEnds(gold, resolver), Matching.of(expected, produced()))
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind != RelationKind.GENERATES }
        .mapNotNull(::itemOf)
        .distinctBy { it.key }

    private fun itemOf(relationship: Relationship): Item? {
        val field = buildConfigFieldOf(relationship)
        val place = codePlaceOf(relationship)
        val script = field?.let { scriptEdges[it.id] }
        if (field == null || place == null || script == null) return null
        val (path, line) = place
        val key = "${NodeRef.OfSymbol(field.id).key()}|$path|$line"
        return Item(key, "${field.name} <- $path:$line", evidenceOf(script))
    }

    private fun buildConfigFieldOf(relationship: Relationship): Symbol? {
        val target = (index.resolvedTarget(relationship) as? NodeRef.OfSymbol)?.let { index.symbolsById[it.id] }
        val generator = (target?.origin as? SymbolOrigin.Generated)?.generator
        return target?.takeIf { generator == BUILD_CONFIG_GENERATOR && it.kind.id == "java.field" }
    }

    private fun codePlaceOf(relationship: Relationship): Pair<String, Int>? {
        val site = relationship.site ?: return null
        val path = site.artifact.path.value
        return (path to site.range.startLine).takeIf { index.artifactsByPath[path]?.type?.id in codeTypes }
    }

    private companion object {
        const val NAME = "buildconfig_field_to_consumer"
    }
}
