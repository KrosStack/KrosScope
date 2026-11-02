package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

private val contractKind = SymbolKind(JNI_FUNCTION_KIND, SymbolCategory.CONTRACT)

/** The symbol and the relationships that stand for a [JniContract]: the contract, and what generates it. */
internal class ContractFacts(private val contract: JniContract) {
    private val external = contract.external
    private val identity = checkNotNull((external.id.identity() as? ParsedSymbolId.Valid)?.identity)
    val symbolId: SymbolId = SymbolIdentity.workspace(
        ModuleId(identity.unit),
        identity.sourceSet,
        SymbolDescriptor(listOf<DescriptorSegment>(DescriptorSegment.Namespace("jni")) + identity.descriptor.segments),
    ).toSymbolId()

    fun facts(): List<Fact> = listOf(Fact.OfSymbol(symbol()), Fact.OfRelationship(generates()))

    private fun certainty() = if (contract.hasAlternateClass) Certainty.PROBABLE else Certainty.EXACT

    private fun symbol() = Symbol(
        id = symbolId,
        kind = contractKind,
        name = contract.shortName,
        qualifiedName = contract.shortName,
        signature = contract.shortName,
        container = null,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Generated(JNI_GENERATOR, NodeRef.OfSymbol(external.id)),
        declaration = null,
        nameLocation = null,
        documentation = null,
        scope = external.scope,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = certainty(),
        attributes = listOfNotNull(
            "jni.short-name" to contract.shortName,
            contract.longName?.let { "jni.long-name" to it },
        ).toMap(),
    )

    private fun generates() = Relationship(
        from = NodeRef.OfSymbol(external.id),
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(symbolId), certainty()),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = external.scope,
    )
}
