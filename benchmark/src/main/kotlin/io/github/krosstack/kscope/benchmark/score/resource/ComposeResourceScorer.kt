package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.AddressResolution
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.GeneratedSurfaces
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.benchmark.score.evidenceOutcome
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind

private val composeKinds = setOf(
    "compose.resource.string",
    "compose.resource.plurals",
    "compose.resource.string-array",
    "compose.resource.drawable",
    "compose.resource.font",
)

/**
 * Each string and drawable of Compose Multiplatform resources to each line of a Kotlin file that reads it through
 * `Res`. The reading is a reference from code to the accessor that the synthesizer generated for the resource; an
 * import of the accessor is not a reading. The evidence is that of the hop from the resource to its accessor.
 */
internal class ComposeResourceScorer(index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val surfaces = GeneratedSurfaces(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val resolved = gold.map { it to resolver.resolve(it.from) }
        val expected = resolved.flatMap { (relationship, from) -> expectedOf(relationship, from) }
        val unmapped = resolved.mapNotNull { (relationship, from) -> unmappedLabel(relationship, from) }
        val matching = Matching.of(expected, produced())
        val evidence = matching.evidenceOutcome()
        return ClassScore(
            name = NAME,
            supported = true,
            goldCount = gold.size,
            score = matching.score(),
            evidenceMismatches = evidence.mismatches,
            unmapped = unmapped,
            acceptedEvidence = evidence.accepted,
        )
    }

    private fun expectedOf(relationship: GoldRelationship, from: AddressResolution): List<Item> {
        val lines = relationship.to.lines.ifEmpty { listOfNotNull(relationship.to.line) }
        val file = relationship.to.file.orEmpty()
        val origin = (from as? AddressResolution.Found)?.node?.key() ?: "unmapped|${relationship.id}"
        return lines.map { Item("$origin|$file|$it", "${relationship.id} $file:$it", relationship.expectedEvidence) }
    }

    private fun unmappedLabel(relationship: GoldRelationship, from: AddressResolution): String? =
        (from as? AddressResolution.Unmapped)?.let { "${relationship.id}: ${it.describe()}" }

    private fun produced(): List<Item> = surfaces.uses
        .filter { it.relationship.kind != RelationKind.IMPORTS }
        .flatMap { use ->
            val line = use.relationship.site?.range?.startLine
            surfaces.sourcesOf(use.target, composeKinds).map { source ->
                val key = "${NodeRef.OfSymbol(source.source.id).key()}|${use.path}|$line"
                Item(key, "${source.source.name} <- ${use.path}:$line", evidenceOf(source.hop))
            }
        }
        .distinctBy { it.key }

    private companion object {
        const val NAME = "compose_resource_to_consumer"
    }
}
