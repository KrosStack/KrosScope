package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor

internal const val ALL = Int.MAX_VALUE

private const val VERSION = "0.1.0"

internal fun workspaceLinker(id: String, capability: AnalyzerCapability) = ProducerDescriptor(
    producer = ProducerRef(id, VERSION),
    consumes = emptySet(),
    capabilities = setOf(capability),
    granularity = OwnershipKind.WORKSPACE,
)

private val stableOrder =
    compareBy<Relationship>({ it.from.sortKey() }, { it.kind }, { it.subKind }, { it.targetKey() })

/** Hands the relationships over in a stable order; a linker without any adds nothing. */
internal fun FactSink.acceptSorted(descriptor: ProducerDescriptor, relationships: List<Relationship>) {
    if (relationships.isEmpty()) return
    val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
    val facts = relationships.sortedWith(stableOrder).map { Fact.OfRelationship(it) }
    accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
}

internal fun NodeRef.sortKey(): String = when (this) {
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
