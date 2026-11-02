package io.github.krosstack.kscope.analyzer.compose.res

import io.github.krosstack.kscope.analyzer.compose.symbolName
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

internal const val RES_GENERATOR = "compose.res"

private val objectKind = SymbolKind("kotlin.object", SymbolCategory.TYPE)
private val propertyKind = SymbolKind("kotlin.property", SymbolCategory.VARIABLE)
private val functionKind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)

/** The parts of a declaration in the stub that differ from one symbol to the next. */
private data class StubDeclaration(
    val id: SymbolId,
    val kind: SymbolKind,
    val qualifiedName: String,
    val signature: String,
    val container: SymbolId?,
    val sourceOfTruth: NodeRef,
    val flags: Set<SymbolFlag> = emptySet(),
)

/**
 * The symbols of the stub of `Res` and the `GENERATES` relationships from the resources to their accessors. Each id is
 * the one that the analyzer computes for the same declaration in the stub: the module and source set of the stub, and
 * the descriptor of the declared package, objects and properties; an extension property is told by its receiver.
 */
internal class ResFacts(private val plan: ResPlan) {
    private val sourceSetName = plan.host.symbolName()
    private val scope = Scope(sourceSet = plan.host.id)
    private val certainty = if (plan.pkg.isExplicit) Certainty.EXACT else Certainty.HEURISTIC
    private val packageDescriptor = plan.pkg.name.split('.').fold(SymbolDescriptor()) { d, name -> d.namespace(name) }
    private val resDescriptor = packageDescriptor.type(RES)
    private val resId = idOf(resDescriptor)
    private val module = NodeRef.OfModule(plan.module.id)
    private val visibility = plan.visibility()

    fun facts(): List<Fact> {
        val symbols = listOf(resDeclaration()) + holders.map(::holderDeclaration) + functionDeclarations() +
            plan.accessors.map(::accessorDeclaration)
        return symbols.map { Fact.OfSymbol(symbolOf(it)) } + generates()
    }

    private fun resDeclaration() = StubDeclaration(
        id = resId,
        kind = objectKind,
        qualifiedName = "${plan.pkg.name}.$RES",
        signature = "$visibility object $RES",
        container = null,
        sourceOfTruth = module,
    )

    private fun holderDeclaration(holder: String) = StubDeclaration(
        id = idOf(resDescriptor.type(holder)),
        kind = objectKind,
        qualifiedName = "${plan.pkg.name}.$RES.$holder",
        signature = "$visibility object $holder",
        container = resId,
        sourceOfTruth = module,
    )

    private fun functionDeclarations(): List<StubDeclaration> = listOf(
        functionDeclaration(READ_BYTES, "suspend fun $READ_BYTES(path: String): ByteArray", setOf(SymbolFlag.SUSPEND)),
        functionDeclaration(GET_URI, "fun $GET_URI(path: String): String", emptySet()),
    )

    private fun functionDeclaration(name: String, header: String, flags: Set<SymbolFlag>) = StubDeclaration(
        id = idOf(resDescriptor.method(name, "String")),
        kind = functionKind,
        qualifiedName = "${plan.pkg.name}.$RES.$name",
        signature = "$visibility $header",
        container = resId,
        sourceOfTruth = module,
        flags = flags,
    )

    private fun accessorDeclaration(accessor: ResAccessor): StubDeclaration {
        val type = accessor.key.type
        return StubDeclaration(
            id = accessorId(accessor),
            kind = propertyKind,
            qualifiedName = "${plan.pkg.name}.${accessor.name}",
            signature = "$visibility val $RES.${type.holder}.${accessor.name}: ${type.accessorType}",
            container = null,
            sourceOfTruth = NodeRef.OfSymbol(accessor.sources.first().symbolId),
        )
    }

    private fun accessorId(accessor: ResAccessor): SymbolId =
        idOf(packageDescriptor.extensionProperty(accessor.name, accessor.key.type.holder))

    private fun generates(): List<Fact> = plan.accessors.flatMap { accessor ->
        accessor.sources.map { source ->
            Fact.OfRelationship(
                Relationship(
                    from = NodeRef.OfSymbol(source.symbolId),
                    kind = RelationKind.GENERATES,
                    subKind = null,
                    resolution = Resolution.Resolved(NodeRef.OfSymbol(accessorId(accessor)), certainty),
                    evidenceClass = EvidenceClass.CONVENTION,
                    site = null,
                    scope = Scope(sourceSet = source.sourceSet.id),
                ),
            )
        }
    }

    private fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(plan.module.id, sourceSetName, descriptor).toSymbolId()

    private fun symbolOf(declaration: StubDeclaration) = Symbol(
        id = declaration.id,
        kind = declaration.kind,
        name = declaration.qualifiedName.substringAfterLast('.'),
        qualifiedName = declaration.qualifiedName,
        signature = declaration.signature,
        container = declaration.container,
        visibility = if (plan.isPublic) Visibility.PUBLIC else Visibility.INTERNAL,
        flags = declaration.flags,
        origin = SymbolOrigin.Generated(RES_GENERATOR, declaration.sourceOfTruth),
        declaration = null,
        nameLocation = null,
        documentation = null,
        scope = scope,
        evidenceClass = EvidenceClass.CONVENTION,
        certainty = certainty,
    )
}
