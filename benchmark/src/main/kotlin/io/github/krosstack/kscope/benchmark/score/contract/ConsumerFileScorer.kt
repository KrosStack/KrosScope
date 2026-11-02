package io.github.krosstack.kscope.benchmark.score.contract

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.AddressResolution
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.ClassPart
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.GeneratedSurfaces
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.SurfaceUse
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.benchmark.score.evidenceOutcome
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol

private const val INFERRED_USAGE = "inferred-type"

/** A use of a generated symbol that belongs to [source]; [namedHop] is set when the use names the generated type. */
private class FileUse(val source: Symbol, val use: SurfaceUse, val namedHop: Relationship?)

/** One expected or produced consumer, with the part of the class it belongs to. */
private class ClassItem(val part: String, val item: Item)

/**
 * Each message, enum or table to each file that uses the type Wire or SQLDelight made from it. The unit is the file,
 * as the class says: a file that names the type is a named consumer, and one that only reads members of values of the
 * type, without naming it, uses it by inference. Gold marks those, and the score is given for both kinds apart.
 */
internal class ConsumerFileScorer(
    private val name: String,
    private val sourceKinds: Set<String>,
    index: GraphIndex,
    private val resolver: GoldAddressResolver,
) {
    private val surfaces = GeneratedSurfaces(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val resolved = gold.map { it to resolver.resolve(it.from) }
        val expected = resolved.map { (relationship, from) -> expectedOf(relationship, from) }
        val produced = producedOf()
        val matching = Matching.of(expected.map { it.item }, produced.map { it.item })
        val outcome = matching.evidenceOutcome()
        return ClassScore(
            name = name,
            supported = true,
            goldCount = gold.size,
            score = matching.score(),
            evidenceMismatches = outcome.mismatches,
            unmapped = resolved.mapNotNull { (relationship, from) -> unmappedLabel(relationship, from) },
            parts = listOf(NAMED, INFERRED).map { partOf(it, expected, produced) },
            acceptedEvidence = outcome.accepted,
        )
    }

    private fun expectedOf(relationship: GoldRelationship, from: AddressResolution): ClassItem {
        val file = relationship.to.file.orEmpty()
        val key = if (from is AddressResolution.Found) "${from.node.key()}|$file" else "unmapped|${relationship.id}"
        val part = if (relationship.to.usage == INFERRED_USAGE) INFERRED else NAMED
        return ClassItem(part, Item(key, "${relationship.id} $file", relationship.expectedEvidence))
    }

    private fun unmappedLabel(relationship: GoldRelationship, from: AddressResolution): String? =
        (from as? AddressResolution.Unmapped)?.let { "${relationship.id}: ${it.describe()}" }

    private fun producedOf(): List<ClassItem> {
        val uses = surfaces.uses.flatMap { use ->
            val type = surfaces.typeSourcesOf(use.target, sourceKinds)
            type.map { FileUse(it.source, use, if (it.isNamed) it.hop else null) } +
                surfaces.typedSourcesOf(use.target, sourceKinds).map { FileUse(it, use, null) }
        }
        return uses.groupBy { it.source.id to it.use.path }.map { (_, group) ->
            val named = group.firstNotNullOfOrNull { it.namedHop }
            val source = group.first().source
            val path = group.first().use.path
            val item = Item("${NodeRef.OfSymbol(source.id).key()}|$path", "${source.name} <- $path")
            if (named != null) {
                ClassItem(NAMED, Item(item.key, item.label, evidenceOf(named)))
            } else {
                ClassItem(INFERRED, Item(item.key, item.label, evidenceOf(group.first().use.relationship)))
            }
        }
    }

    private fun partOf(part: String, expected: List<ClassItem>, produced: List<ClassItem>): ClassPart {
        val matching = Matching.of(
            expected.filter { it.part == part }.map { it.item },
            produced.filter { it.part == part }.map { it.item },
        )
        val outcome = matching.evidenceOutcome()
        return ClassPart(part, matching.score(), outcome.mismatches, outcome.accepted)
    }

    companion object {
        const val NAMED = "named"
        const val INFERRED = INFERRED_USAGE
    }
}

internal fun protoScorer(index: GraphIndex, resolver: GoldAddressResolver) =
    ConsumerFileScorer("proto_message_to_consumer", setOf("proto.message", "proto.enum"), index, resolver)

internal fun sqlTableScorer(index: GraphIndex, resolver: GoldAddressResolver) =
    ConsumerFileScorer("sq_table_to_consumer", setOf("sqldelight.table"), index, resolver)
