package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSymbols
import io.github.krosstack.kscope.analyzer.android.aidl.signature
import io.github.krosstack.kscope.analyzer.android.resources.symbolName
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

internal const val AIDL_GENERATOR = "android.aidl"

private val methodKind = SymbolKind("java.method", SymbolCategory.CALLABLE)
private val fieldKind = SymbolKind("java.field", SymbolCategory.VARIABLE)

/** Where a member sits in the stub: its descriptor, its qualified name and the id of the member that holds it. */
private class Placement(val descriptor: SymbolDescriptor, val qualifiedName: String, val container: SymbolId?)

/**
 * The symbols of a planned stub and the `GENERATES` relationships from the AIDL symbols they come from. Each id is the
 * one that the analyzer computes for the same declaration in the stub: the module and source set that host it, and the
 * descriptor of the package, the classes and the member.
 */
internal class AidlStubFacts(private val module: Module, host: SourceSet) {
    private val sourceSetName = host.symbolName()
    private val scope = Scope(sourceSet = host.id)

    fun facts(packageName: String?, type: StubType): List<Fact> {
        val packageDescriptor = AidlSymbols.packageSegments(packageName)
            .fold(SymbolDescriptor()) { descriptor, segment -> descriptor.namespace(segment) }
        val root = Placement(packageDescriptor, packageName.orEmpty(), null)
        return factsOf(type, root, type.truth?.let { NodeRef.OfSymbol(idOf(it)) })
    }

    private fun factsOf(member: StubMember, holder: Placement, truth: NodeRef?): List<Fact> {
        val placement = placementOf(member, holder)
        val id = idOf(placement.descriptor)
        val own = Fact.OfSymbol(symbolOf(member, placement, id, truth))
        val generates = member.truth?.let { Fact.OfRelationship(generates(it, id)) }
        val nested = (member as? StubType)?.members.orEmpty().flatMap { factsOf(it, placement.forChildren(id), truth) }
        return listOfNotNull(own, generates) + nested
    }

    private fun Placement.forChildren(id: SymbolId) = Placement(descriptor, qualifiedName, id)

    private fun placementOf(member: StubMember, holder: Placement): Placement {
        val descriptor = when (member) {
            is StubType -> holder.descriptor.type(member.name)
            is StubMethod -> holder.descriptor.method(member.name, member.disambiguator)
            is StubField -> holder.descriptor.term(member.name)
        }
        val qualifiedName = listOf(holder.qualifiedName, member.name).filter { it.isNotEmpty() }.joinToString(".")
        return Placement(descriptor, qualifiedName, holder.container)
    }

    private fun generates(truth: SymbolDescriptor, generated: SymbolId) = Relationship(
        from = NodeRef.OfSymbol(AidlSymbols.idOf(module.id, sourceSetName, truth)),
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(generated), Certainty.EXACT),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = scope,
    )

    private fun idOf(descriptor: SymbolDescriptor): SymbolId = AidlSymbols.idOf(module.id, sourceSetName, descriptor)

    private fun symbolOf(member: StubMember, placement: Placement, id: SymbolId, truth: NodeRef?) = Symbol(
        id = id,
        kind = (member as? StubType)?.kind ?: if (member is StubMethod) methodKind else fieldKind,
        name = member.name,
        qualifiedName = placement.qualifiedName,
        signature = member.signature,
        container = placement.container,
        visibility = Visibility.PUBLIC,
        flags = member.flags,
        origin = SymbolOrigin.Generated(AIDL_GENERATOR, truth),
        declaration = null,
        nameLocation = null,
        documentation = null,
        scope = scope,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.EXACT,
        attributes = mapOf(ROLE_ATTRIBUTE to member.role.value),
    )
}
