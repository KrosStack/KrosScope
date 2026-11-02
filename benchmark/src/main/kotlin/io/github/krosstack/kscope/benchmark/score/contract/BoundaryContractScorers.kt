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
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol

private const val JNI_CONTRACT_KIND = "jni.function"

private fun GraphIndex.symbolOf(node: NodeRef?): Symbol? = (node as? NodeRef.OfSymbol)?.let { symbolsById[it.id] }

private fun GraphIndex.fileOf(node: NodeRef?): String? = symbolOf(node)?.declaration?.artifact?.path?.value

private fun unmappedOf(gold: List<GoldRelationship>, resolver: GoldAddressResolver): List<String> =
    gold.mapNotNull { relationship ->
        val from = resolver.resolve(relationship.from) as? AddressResolution.Unmapped
        from?.let { "${relationship.id}: ${it.describe()}" }
    }

/**
 * The classes that bind an AIDL contract to Kotlin and Java code: an interface to its implementers and its clients, a
 * method to its overrides and a parcelable to its class. The contract end is the AIDL symbol of gold. The code end is
 * the file that holds the implementer, client, override or class, because an object or a member inside an object is not
 * a symbol of its own: the graph states the nearest symbol around it, which is in the same file.
 */
internal class AidlScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    fun implementers(gold: List<GoldRelationship>) = score(gold, "aidl_interface_to_implementer", "implementer")

    fun clients(gold: List<GoldRelationship>) = score(gold, "aidl_interface_to_client", "client")

    fun overrides(gold: List<GoldRelationship>) = score(gold, "aidl_method_to_override", "override")

    fun parcelables(gold: List<GoldRelationship>) = score(gold, "aidl_parcelable_to_class", "parcelable-class")

    private fun score(gold: List<GoldRelationship>, name: String, subKind: String): ClassScore {
        val expected = gold.map { expectedItem(it, resolver.resolve(it.from)) }
        val mapped = MappedClass(emptyList(), unmappedOf(gold, resolver))
        return scoreOf(name, gold, mapped, Matching.of(expected, produced(subKind)))
    }

    private fun expectedItem(relationship: GoldRelationship, from: AddressResolution): Item {
        val file = relationship.to.file.orEmpty()
        val key = if (from is AddressResolution.Found) "${from.node.key()}|$file" else "unmapped|${relationship.id}"
        return Item(key, "${relationship.id} -> $file", relationship.expectedEvidence)
    }

    private fun produced(subKind: String): List<Item> = index.relationships
        .filter { it.kind == RelationKind.BINDS && it.subKind == subKind }
        .mapNotNull { relationship ->
            val file = index.fileOf(index.resolvedTarget(relationship))
            file?.let { itemOf(relationship, it) }
        }

    private fun itemOf(relationship: Relationship, file: String): Item {
        val label = "${index.symbolOf(relationship.from)?.name} -> $file"
        return Item("${relationship.from.key()}|$file", label, evidenceOf(relationship))
    }
}

/**
 * Every Kotlin `external` function to the C function that implements it. The graph states it in two hops: the
 * declaration generates a JNI contract, and the contract binds to the native function. The evidence is that of the
 * second hop, which names the function.
 */
internal class JniExternalScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val declarationsByContract: Map<NodeRef, NodeRef> = index.relationships
        .filter { it.kind == RelationKind.GENERATES }
        .mapNotNull { generates ->
            val contract = index.resolvedTarget(generates)
            val isContract = index.symbolOf(contract)?.kind?.id == JNI_CONTRACT_KIND
            if (contract != null && isContract) contract to generates.from else null
        }.toMap()

    fun score(gold: List<GoldRelationship>): ClassScore {
        val expected = gold.map { expectedItem(it, resolver.resolve(it.from)) }
        val mapped = MappedClass(emptyList(), unmappedOf(gold, resolver))
        return scoreOf(NAME, gold, mapped, Matching.of(expected, produced()))
    }

    private fun expectedItem(relationship: GoldRelationship, from: AddressResolution): Item {
        val target = "${relationship.to.file}|${relationship.to.name}"
        val key = if (from is AddressResolution.Found) "${from.node.key()}|$target" else "unmapped|${relationship.id}"
        return Item(key, "${relationship.id} -> $target", relationship.expectedEvidence)
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.BINDS && it.resolution is Resolution.Resolved }
        .mapNotNull { binds -> declarationsByContract[binds.from]?.let { itemOf(it, binds) } }

    private fun itemOf(declaration: NodeRef, binds: Relationship): Item? {
        val native = index.symbolOf(index.resolvedTarget(binds)) ?: return null
        val target = "${native.declaration?.artifact?.path?.value}|${native.name}"
        val label = "${index.symbolOf(declaration)?.name} -> $target"
        return Item("${declaration.key()}|$target", label, evidenceOf(binds))
    }

    private companion object {
        const val NAME = "jni_external_to_native"
    }
}
