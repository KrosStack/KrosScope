package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.AddressResolution
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.ClassPart
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.evidenceOutcome
import io.github.krosstack.kscope.model.NodeRef

/** An expected or produced end of the class, with the kind of consumer it belongs to. */
private class PartItem(val part: String, val item: Item)

/**
 * Each declaration of a string, dimension, color, style, drawable, layout, menu or navigation graph to each file and
 * line that refers to it. The graph is asked for the consumers of the resource types that gold covers only. The score
 * is given for all consumers and for each kind of consumer apart, so that kinds without a producer, such as the
 * manifest, show as what they are.
 */
internal class ResourceToConsumerScorer(index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val consumers = ResourceConsumers(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val resolved = gold.map { it to resolver.resolve(it.from) }
        val covered = gold.mapNotNull { it.from.kind }.toSet()
        val expected = resolved.flatMap { (relationship, from) -> expectedOf(relationship, from) }
        val unmapped = resolved.mapNotNull { (relationship, from) -> unmappedLabel(relationship, from) }
        val produced = producedOf(covered)
        val matching = Matching.of(expected.map { it.item }, produced.map { it.item })
        val evidence = matching.evidenceOutcome()
        return ClassScore(
            name = NAME,
            supported = true,
            goldCount = gold.size,
            score = matching.score(),
            evidenceMismatches = evidence.mismatches,
            unmapped = unmapped,
            acceptedEvidence = evidence.accepted,
            parts = ConsumerParts.all.map { part -> partOf(part, expected, produced) },
        )
    }

    private fun expectedOf(relationship: GoldRelationship, from: AddressResolution): List<PartItem> {
        val part = consumerPartOf(relationship.from.kind.orEmpty(), expectedConsumerType(relationship))
        val lines = relationship.to.lines.ifEmpty { listOfNotNull(relationship.to.line) }
        val consumer = relationship.to.file.orEmpty()
        return lines.map { line ->
            val key = if (from is AddressResolution.Found) {
                "${from.node.key()}|$consumer|$line"
            } else {
                "unmapped|${relationship.id}|$line"
            }
            PartItem(part, Item(key, "${relationship.id} $consumer:$line", relationship.expectedEvidence))
        }
    }

    private fun expectedConsumerType(relationship: GoldRelationship): String = when {
        relationship.to.kind == "consumer.xml.manifest" -> "android.manifest"
        relationship.to.kind?.startsWith("consumer.xml.") == true -> "android.resource"
        else -> "kotlin.source"
    }

    private fun unmappedLabel(relationship: GoldRelationship, from: AddressResolution): String? =
        (from as? AddressResolution.Unmapped)?.let { "${relationship.id}: ${it.describe()}" }

    private fun producedOf(covered: Set<String>): List<PartItem> = consumers.edges()
        .filter { it.resource.kind.id in covered }
        .map { edge ->
            val key = "${NodeRef.OfSymbol(edge.resource.id).key()}|${edge.path}|${edge.line}"
            val part = consumerPartOf(edge.resource.kind.id, consumers.typeOf(edge.path))
            PartItem(part, Item(key, "${edge.resource.name} <- ${edge.path}:${edge.line}", edge.evidence))
        }

    private fun partOf(part: String, expected: List<PartItem>, produced: List<PartItem>): ClassPart {
        val matching = Matching.of(
            expected.filter { it.part == part }.map { it.item },
            produced.filter { it.part == part }.map { it.item },
        )
        val evidence = matching.evidenceOutcome()
        return ClassPart(part, matching.score(), evidence.mismatches, evidence.accepted)
    }

    private companion object {
        const val NAME = "resource_to_consumer"
    }
}
