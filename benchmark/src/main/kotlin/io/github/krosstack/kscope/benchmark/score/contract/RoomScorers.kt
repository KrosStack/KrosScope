package io.github.krosstack.kscope.benchmark.score.contract

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
import io.github.krosstack.kscope.benchmark.score.evidenceOutcome
import io.github.krosstack.kscope.benchmark.score.expectedItems
import io.github.krosstack.kscope.benchmark.score.pairKey
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.benchmark.score.unmappedMisses
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol

private val roomRoles = listOf("Entity", "Dao", "Database")
private val codeTypes = setOf("kotlin.source", "java.source")

private fun Symbol.isRoom(role: String): Boolean = attributes["annotations"].orEmpty().split(' ')
    .any { it == "androidx.room.$role" || it == role }

/**
 * An entity with its DAO, its database and the schema file of the database. The graph states how a database or a DAO
 * relates to an entity (`BINDS`, from the database or the DAO), and the database to its DAOs and its schema, so the
 * first two are turned around to be compared with gold, which lists them from the entity.
 */
internal class RoomStructureScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        return scoreOf(NAME, gold, mapped, Matching.of(expected, produced()))
    }

    private fun produced(): List<Item> = index.relationships.filter { it.kind == RelationKind.BINDS }
        .mapNotNull(::itemOf).distinctBy { it.key }

    private fun itemOf(relationship: Relationship): Item? {
        val from = (relationship.from as? NodeRef.OfSymbol)?.let { index.symbolsById[it.id] }
        val target = index.resolvedTarget(relationship)
        val isRoom = from != null && roomRoles.any(from::isRoom)
        val isStructure = relationship.subKind in STRUCTURE
        val isTurned = relationship.subKind in TURNED_AROUND
        val key = target?.let { if (isTurned) pairKey(it, relationship.from) else pairKey(relationship.from, it) }
        return if (!isRoom || !isStructure || key == null) null else Item(key, "binds", evidenceOf(relationship))
    }

    private companion object {
        const val NAME = "room_entity_dao_database"
        val TURNED_AROUND = setOf("entity", "dao")
        val STRUCTURE = TURNED_AROUND + "exported-schema"
    }
}

/**
 * Each DAO and database to each file outside the Room declarations that uses it, imports it or implements it. The unit
 * is the file: gold lists the lines where a consumer names the component or the class that implements it.
 */
internal class RoomConsumerScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val components: Map<String, Symbol> = index.symbols.filter { it.isRoom("Dao") || it.isRoom("Database") }
        .associateBy { it.id.value }
    private val declaringFiles: Set<String> = index.symbols.filter { symbol -> roomRoles.any(symbol::isRoom) }
        .mapNotNull { it.declaration?.artifact?.path?.value }.toSet()

    fun score(gold: List<GoldRelationship>): ClassScore {
        val resolved = gold.map { it to resolver.resolve(it.from) }
        val expected = resolved.map { (relationship, from) -> expectedOf(relationship, from) }
        val unmapped = resolved.mapNotNull { (relationship, from) ->
            (from as? AddressResolution.Unmapped)?.let { "${relationship.id}: ${it.describe()}" }
        }
        val matching = Matching.of(expected, produced())
        val outcome = matching.evidenceOutcome()
        return ClassScore(
            name = NAME,
            supported = true,
            goldCount = gold.size,
            score = matching.score(),
            evidenceMismatches = outcome.mismatches,
            unmapped = unmapped,
            acceptedEvidence = outcome.accepted,
        )
    }

    private fun expectedOf(relationship: GoldRelationship, from: AddressResolution): Item {
        val file = relationship.to.file.orEmpty()
        val key = if (from is AddressResolution.Found) "${from.node.key()}|$file" else "unmapped|${relationship.id}"
        return Item(key, "${relationship.id} $file", relationship.expectedEvidence)
    }

    private fun produced(): List<Item> = index.relationships.mapNotNull(::itemOf).distinctBy { it.key }

    private fun itemOf(relationship: Relationship): Item? {
        val path = relationship.site?.artifact?.path?.value
        val target = (index.resolvedTarget(relationship) as? NodeRef.OfSymbol)?.let { index.symbolsById[it.id] }
        val owner = target?.container?.let { components[it.value] }
        val component = target?.let { components[it.id.value] } ?: owner
        val isCode = path != null && index.artifactsByPath[path]?.type?.id in codeTypes
        val isConsumer = isCode && path !in declaringFiles && relationship.kind != RelationKind.BINDS
        return if (component == null || !isConsumer) {
            null
        } else {
            val key = "${NodeRef.OfSymbol(component.id).key()}|$path"
            Item(key, "${component.name} <- $path", evidenceOf(relationship))
        }
    }

    private companion object {
        const val NAME = "room_component_to_consumer"
    }
}
