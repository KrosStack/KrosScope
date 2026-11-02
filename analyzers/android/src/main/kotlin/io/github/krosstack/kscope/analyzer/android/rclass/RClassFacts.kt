package io.github.krosstack.kscope.analyzer.android.rclass

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

internal const val R_GENERATOR = "android.r"

private val classKind = SymbolKind("java.class", SymbolCategory.TYPE)
private val fieldKind = SymbolKind("java.field", SymbolCategory.VARIABLE)

/** The parts of a declaration in the stub that differ from one symbol to the next. */
private data class StubDeclaration(
    val id: SymbolId,
    val kind: SymbolKind,
    val name: String,
    val qualifiedName: String,
    val signature: String,
    val container: SymbolId?,
) {
    val flags: Set<SymbolFlag> = if (container == null) emptySet() else setOf(SymbolFlag.STATIC)
}

/**
 * The symbols of the stub of `R` and the `GENERATES` relationships from the resources to their fields. Each id is the
 * one that the analyzer computes for the same declaration in the stub: the module and source set of the stub, and the
 * descriptor of the declared package, classes and fields.
 */
internal class RClassFacts(private val plan: RClassPlan) {
    private val sourceSetName = plan.host.symbolName()
    private val scope = Scope(sourceSet = plan.host.id)
    private val rDescriptor = plan.namespace.split('.').fold(SymbolDescriptor()) { d, name -> d.namespace(name) }
        .type("R")
    private val rId = idOf(rDescriptor)

    fun facts(): List<Fact> {
        val module = NodeRef.OfModule(plan.module.id)
        val root = StubDeclaration(rId, classKind, "R", "${plan.namespace}.R", "public final class R", null)
        return listOf<Fact>(Fact.OfSymbol(symbolOf(root, module))) + plan.types.flatMap { typeFacts(it, module) }
    }

    private fun typeFacts(type: RType, module: NodeRef): List<Fact> {
        val descriptor = rDescriptor.type(type.name)
        val declaration = StubDeclaration(
            id = idOf(descriptor),
            kind = classKind,
            name = type.name,
            qualifiedName = "${plan.namespace}.R.${type.name}",
            signature = "public static final class ${type.name}",
            container = rId,
        )
        val fields = type.fields.flatMap { fieldFacts(type, it, descriptor, declaration.id) }
        return listOf<Fact>(Fact.OfSymbol(symbolOf(declaration, module))) + fields
    }

    private fun fieldFacts(type: RType, field: RField, typeDescriptor: SymbolDescriptor, typeId: SymbolId): List<Fact> {
        val javaType = if (field.isArray) "int[]" else "int"
        val declaration = StubDeclaration(
            id = idOf(typeDescriptor.term(field.name)),
            kind = fieldKind,
            name = field.name,
            qualifiedName = "${plan.namespace}.R.${type.name}.${field.name}",
            signature = "public static final $javaType ${field.name}",
            container = typeId,
        )
        val source = NodeRef.OfSymbol(field.sources.first().symbolId)
        val edges = field.sources.map { Fact.OfRelationship(generates(it, declaration.id)) }
        return listOf<Fact>(Fact.OfSymbol(symbolOf(declaration, source))) + edges
    }

    private fun generates(source: OwnedDefinition, field: SymbolId) = Relationship(
        from = NodeRef.OfSymbol(source.symbolId),
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(field), Certainty.EXACT),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = Scope(sourceSet = source.sourceSet.id),
    )

    private fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(plan.module.id, sourceSetName, descriptor).toSymbolId()

    private fun symbolOf(declaration: StubDeclaration, sourceOfTruth: NodeRef) = Symbol(
        id = declaration.id,
        kind = declaration.kind,
        name = declaration.name,
        qualifiedName = declaration.qualifiedName,
        signature = declaration.signature,
        container = declaration.container,
        visibility = Visibility.PUBLIC,
        flags = declaration.flags,
        origin = SymbolOrigin.Generated(R_GENERATOR, sourceOfTruth),
        declaration = null,
        nameLocation = null,
        documentation = null,
        scope = scope,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.EXACT,
    )
}
