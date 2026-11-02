package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.AddressResolution
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.model.NodeRef
import kotlinx.serialization.Serializable

/**
 * The agreement for one relationship class. A class that no producer supports yet has [supported] false and no score;
 * [goldCount] says how many relationships it will have to find. [evidenceMismatches] counts matched relationships
 * whose evidence class [EvidencePolicy] does not accept for the expected one, and [unmapped] the gold addresses that
 * could not be mapped. [acceptedEvidence] counts by produced class the matches whose class differs from the expected
 * one but is accepted. [parts] splits a class whose ends are of several kinds, so that what one kind misses is not
 * hidden in the total.
 */
@Serializable
public data class ClassScore(
    public val name: String,
    public val supported: Boolean,
    public val goldCount: Int,
    public val score: Score?,
    public val evidenceMismatches: Int,
    public val unmapped: List<String>,
    public val parts: List<ClassPart> = emptyList(),
    public val acceptedEvidence: Map<String, Int> = emptyMap(),
)

/** The agreement for one kind of end of a class, such as the XML consumers of resources. */
@Serializable
public data class ClassPart(
    public val label: String,
    public val score: Score,
    public val evidenceMismatches: Int,
    public val acceptedEvidence: Map<String, Int> = emptyMap(),
)

/** A gold relationship whose two ends were mapped to nodes. */
internal class MappedRelationship(val gold: GoldRelationship, val from: NodeRef, val to: NodeRef)

/**
 * The gold relationships of a class with their ends mapped, and the ends that could not be mapped. [unmapped] has one
 * entry for each such end, to show in a report; [incomplete] has one for each relationship that has such an end.
 */
internal class MappedClass(
    val mapped: List<MappedRelationship>,
    val unmapped: List<String>,
    val incomplete: List<String> = unmapped,
) {
    companion object {
        fun of(gold: List<GoldRelationship>, resolver: GoldAddressResolver): MappedClass {
            val results = gold.map { Triple(it, resolver.resolve(it.from), resolver.resolve(it.to)) }
            val mapped = results.mapNotNull { (relationship, from, to) ->
                if (from is AddressResolution.Found && to is AddressResolution.Found) {
                    MappedRelationship(relationship, from.node, to.node)
                } else {
                    null
                }
            }
            val unmappedEnds = results.map { (relationship, from, to) ->
                relationship to listOf(from, to).filterIsInstance<AddressResolution.Unmapped>()
            }
            val unmapped = unmappedEnds.flatMap { (relationship, ends) ->
                ends.map { "${relationship.id}: ${it.describe()}" }
            }
            val incomplete = unmappedEnds.filter { (_, ends) -> ends.isNotEmpty() }.map { (relationship, ends) ->
                ends.joinToString("; ", "${relationship.id}: ") { it.describe() }
            }
            return MappedClass(mapped, unmapped, incomplete)
        }
    }
}

/** Items for the mapped relationships: what a class compares is the key, the expected evidence is the group. */
internal fun MappedClass.expectedItems(keyOf: (MappedRelationship) -> String): List<Item> = mapped.map {
    Item(keyOf(it), it.gold.id, it.gold.expectedEvidence)
}

internal fun pairKey(from: NodeRef, to: NodeRef): String = "${from.key()}|${to.key()}"

/** A relationship with an unmapped end is one miss, whatever the number of its unmapped ends. */
internal fun MappedClass.unmappedMisses(): List<Item> = incomplete.map { Item("unmapped|$it", "unmapped $it") }

internal fun scoreOf(
    name: String,
    gold: List<GoldRelationship>,
    mapped: MappedClass,
    matching: Matching,
    checksEvidence: Boolean = true,
): ClassScore {
    val evidence = if (checksEvidence) matching.evidenceOutcome() else EvidenceOutcome(0, emptyMap())
    return ClassScore(
        name = name,
        supported = true,
        goldCount = gold.size,
        score = matching.score(),
        evidenceMismatches = evidence.mismatches,
        unmapped = mapped.unmapped,
        acceptedEvidence = evidence.accepted,
    )
}
