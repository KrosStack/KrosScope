package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.resources.symbolName
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

internal const val VIEW_BINDING_GENERATOR = "android.viewbinding"

private val classKind = SymbolKind("java.class", SymbolCategory.TYPE)
private val fieldKind = SymbolKind("java.field", SymbolCategory.VARIABLE)
private val methodKind = SymbolKind("java.method", SymbolCategory.CALLABLE)

/** A declaration in a stub, with what differs from one symbol to the next. */
private data class StubDeclaration(
    val descriptor: SymbolDescriptor,
    val kind: SymbolKind,
    val name: String,
    val signature: String,
    val container: SymbolId?,
    val flags: Set<SymbolFlag> = emptySet(),
)

/**
 * The symbols of the stub of one binding class, and the `GENERATES` relationships from the layout resources to the
 * class and from the id resources to the fields. Each id is the one that the analyzer computes for the same
 * declaration in the stub: the module and source set of the stub, and the descriptor of its package, class and members.
 */
internal class BindingFacts(private val plan: BindingPlan, private val binding: BindingClass) {
    private val scope = Scope(sourceSet = plan.host.id)
    private val sourceSetName = plan.host.symbolName()
    private val classDescriptor = plan.packageName.split('.')
        .fold(SymbolDescriptor()) { descriptor, name -> descriptor.namespace(name) }.type(binding.name)
    private val classId = idOf(classDescriptor)
    private val layoutSource = binding.layoutSymbols.first()

    fun facts(): List<Fact> = classFacts() + binding.fields.flatMap(::fieldFacts) + binding.methods().map(::methodFact)

    private fun classFacts(): List<Fact> {
        val declaration = StubDeclaration(classDescriptor, classKind, binding.name, binding.classSignature, null)
        val edges = binding.layoutSymbols.map { Fact.OfRelationship(generates(it, classId)) }
        return listOf<Fact>(Fact.OfSymbol(symbolOf(declaration, layoutSource))) + edges
    }

    private fun fieldFacts(field: BindingField): List<Fact> {
        val declaration = StubDeclaration(
            descriptor = classDescriptor.term(field.name),
            kind = fieldKind,
            name = field.name,
            signature = binding.fieldSignature(field),
            container = classId,
        )
        val edges = field.sources.map { Fact.OfRelationship(generates(it, idOf(declaration.descriptor))) }
        return listOf<Fact>(Fact.OfSymbol(symbolOf(declaration, field.sources.firstOrNull() ?: layoutSource))) + edges
    }

    private fun methodFact(method: MethodSpec): Fact {
        val declaration = StubDeclaration(
            descriptor = classDescriptor.method(method.name, method.disambiguator),
            kind = methodKind,
            name = method.name,
            signature = method.signature,
            container = classId,
            flags = if (method.isStatic) setOf(SymbolFlag.STATIC) else emptySet(),
        )
        return Fact.OfSymbol(symbolOf(declaration, layoutSource))
    }

    private fun generates(source: SymbolId, target: SymbolId) = Relationship(
        from = NodeRef.OfSymbol(source),
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(target), Certainty.EXACT),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = scope,
    )

    private fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(plan.module.id, sourceSetName, descriptor).toSymbolId()

    private fun symbolOf(declaration: StubDeclaration, sourceOfTruth: SymbolId) = Symbol(
        id = idOf(declaration.descriptor),
        kind = declaration.kind,
        name = declaration.name,
        qualifiedName = qualifiedNameOf(declaration),
        signature = declaration.signature,
        container = declaration.container,
        visibility = Visibility.PUBLIC,
        flags = declaration.flags,
        origin = SymbolOrigin.Generated(VIEW_BINDING_GENERATOR, NodeRef.OfSymbol(sourceOfTruth)),
        declaration = null,
        nameLocation = null,
        documentation = null,
        scope = scope,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.EXACT,
    )

    private fun qualifiedNameOf(declaration: StubDeclaration): String {
        val owner = "${plan.packageName}.${binding.name}"
        return if (declaration.container == null) owner else "$owner.${declaration.name}"
    }
}
