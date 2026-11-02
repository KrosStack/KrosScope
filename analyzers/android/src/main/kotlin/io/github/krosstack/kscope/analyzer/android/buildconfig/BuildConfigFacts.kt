package io.github.krosstack.kscope.analyzer.android.buildconfig

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

internal const val BUILD_CONFIG_GENERATOR = "android.buildconfig"

private val classKind = SymbolKind("java.class", SymbolCategory.TYPE)
private val fieldKind = SymbolKind("java.field", SymbolCategory.VARIABLE)

/** The parts of a declaration in the stub that differ from one symbol to the next. */
private data class StubDeclaration(
    val id: SymbolId,
    val kind: SymbolKind,
    val name: String,
    val qualifiedName: String,
    val signature: String,
    val container: SymbolId? = null,
    val flags: Set<SymbolFlag> = emptySet(),
)

/**
 * The symbols of the stub of `BuildConfig` and the `GENERATES` relationships from the build script to the class and to
 * each field. Each id is the one that the analyzer computes for the same declaration in the stub.
 */
internal class BuildConfigFacts(private val plan: BuildConfigPlan) {
    private val scope = Scope(sourceSet = plan.host.id)
    private val sourceOfTruth = NodeRef.OfArtifact(plan.script.ref)
    private val classDescriptor = plan.namespace.split('.')
        .fold(SymbolDescriptor()) { descriptor, name -> descriptor.namespace(name) }.type(BuildConfigPlan.CLASS_NAME)
    private val classId = idOf(classDescriptor)

    fun facts(): List<Fact> = classFacts() + plan.fields.flatMap(::fieldFacts)

    private fun classFacts(): List<Fact> {
        val declaration = StubDeclaration(
            id = classId,
            kind = classKind,
            name = BuildConfigPlan.CLASS_NAME,
            qualifiedName = plan.className,
            signature = BuildConfigPlan.CLASS_SIGNATURE,
        )
        return factsOf(declaration)
    }

    private fun fieldFacts(field: ConfigField): List<Fact> {
        val declaration = StubDeclaration(
            id = idOf(classDescriptor.term(field.name)),
            kind = fieldKind,
            name = field.name,
            qualifiedName = "${plan.className}.${field.name}",
            signature = field.signature,
            container = classId,
            flags = setOf(SymbolFlag.STATIC),
        )
        return factsOf(declaration)
    }

    private fun factsOf(declaration: StubDeclaration): List<Fact> =
        listOf(Fact.OfSymbol(symbolOf(declaration)), Fact.OfRelationship(generates(declaration.id)))

    private fun generates(target: SymbolId) = Relationship(
        from = sourceOfTruth,
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(target), Certainty.EXACT),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = scope,
    )

    private fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(plan.module.id, plan.host.symbolName(), descriptor).toSymbolId()

    private fun symbolOf(declaration: StubDeclaration) = Symbol(
        id = declaration.id,
        kind = declaration.kind,
        name = declaration.name,
        qualifiedName = declaration.qualifiedName,
        signature = declaration.signature,
        container = declaration.container,
        visibility = Visibility.PUBLIC,
        flags = declaration.flags,
        origin = SymbolOrigin.Generated(BUILD_CONFIG_GENERATOR, sourceOfTruth),
        declaration = null,
        nameLocation = null,
        documentation = null,
        scope = scope,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = Certainty.EXACT,
    )
}
