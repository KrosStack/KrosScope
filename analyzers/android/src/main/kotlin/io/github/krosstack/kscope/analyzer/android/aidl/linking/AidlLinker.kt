package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.AidlArtifactTypes
import io.github.krosstack.kscope.analyzer.android.aidl.AidlSymbols
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val IMPLEMENTER = "implementer"
internal const val CLIENT = "client"
internal const val OVERRIDE = "override"
internal const val PARCELABLE_CLASS = "parcelable-class"
internal const val ALL = Int.MAX_VALUE

/**
 * Joins the AIDL contracts to the code that uses them across the generated surface. An interface is bound to the
 * classes and objects that extend its `Stub` and to the classes that refer to it or call `asInterface`; a method to the
 * methods that override it; an unstructured parcelable to the class of the same qualified name. The compiler edges
 * into the generated stub are the evidence, and the `GENERATES` edge from the contract to the stub is the convention
 * that carries them back to the `.aidl` file.
 */
internal class AidlLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.aidl-linker", "0.1.0"),
        consumes = setOf(AidlArtifactTypes.source),
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun link(graph: GraphReader, sink: FactSink) {
        val interfaces = graph.symbols(SymbolQuery(kindId = AidlSymbols.INTERFACE_KIND, limit = ALL))
        val parcelables = graph.symbols(SymbolQuery(kindId = AidlSymbols.PARCELABLE_KIND, limit = ALL))
        val relationships = interfaces.flatMap { InterfaceLinks(graph, it).relationships() } +
            parcelables.flatMap { ParcelableLinks(graph, it).relationships() }
        if (relationships.isEmpty()) return
        val ordered = relationships.sortedWith(compareBy({ it.from.sortKey() }, { it.subKind }, { it.targetKey() }))
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), ordered.map { Fact.OfRelationship(it) }))
    }
}

private fun NodeRef.sortKey(): String = when (this) {
    is NodeRef.OfSymbol -> id.value
    is NodeRef.OfArtifact -> ref.path.value
    is NodeRef.OfModule -> id.value
    is NodeRef.OfSourceSet -> id.value
    is NodeRef.OfExternal -> coordinate
}

private fun Relationship.targetKey(): String = when (val resolution = resolution) {
    is Resolution.Resolved -> resolution.target.sortKey()
    is Resolution.Unresolved -> resolution.hint
    is Resolution.NotAnalysed -> resolution.hint
}

/** One edge of the result: [from] is the contract symbol, [target] the code side and [certainty] the compiler's. */
internal data class Binding(
    val from: Symbol,
    val target: NodeRef,
    val subKind: String,
    val certainty: Certainty,
    val scope: Scope,
    val attributes: Map<String, String> = emptyMap(),
)

/** The relationship that binds a contract to the code side of a [binding]; it is expressed in the `.aidl` file. */
internal fun bindingOf(binding: Binding, evidence: EvidenceClass = EvidenceClass.CONVENTION) = Relationship(
    from = NodeRef.OfSymbol(binding.from.id),
    kind = RelationKind.BINDS,
    subKind = binding.subKind,
    resolution = Resolution.Resolved(binding.target, binding.certainty),
    evidenceClass = evidence,
    site = binding.from.nameLocation,
    scope = binding.scope,
    attributes = binding.attributes,
)

internal fun Relationship.certainty(): Certainty =
    (resolution as? Resolution.Resolved)?.certainty ?: Certainty.HEURISTIC
