package io.github.krosstack.kscope.analyzer.schema.stub

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.namespaces
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
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
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

private const val CONSTRUCTOR_NAME = "<init>"
private const val COMPANION_NAME = "Companion"

/** The attribute that says a generated declaration has a type that is generated from the named symbol. */
internal const val TYPE_SOURCE_ATTRIBUTE = "generated.type-source"

private val classKind = typeKind("kotlin.class")
private val propertyKind = SymbolKind("kotlin.property", SymbolCategory.VARIABLE)
private val entryKind = SymbolKind("kotlin.enum-entry", SymbolCategory.VARIABLE)
private val functionKind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)
private val constructorKind = SymbolKind("kotlin.constructor", SymbolCategory.CALLABLE)

private fun typeKind(id: String) = SymbolKind(id, SymbolCategory.TYPE)

/** The symbols of a stub and the `GENERATES` relationships from its sources of truth. */
internal class StubFacts(val symbols: List<Symbol>, val relationships: List<Relationship>)

/** What differs from one declaration of a stub to the next; the rest follows from where the stub is. */
private data class Draft(
    val id: SymbolId,
    val kind: SymbolKind,
    val qualifiedName: String,
    val signature: String,
    val container: SymbolId?,
    val truth: StubTruth? = null,
    val certainty: Certainty = Certainty.EXACT,
    val flags: Set<SymbolFlag> = emptySet(),
    val typeSource: SymbolId? = null,
)

/** The class whose members are being added: where it is and what it was generated from. */
private class Owner(val descriptor: SymbolDescriptor, val id: SymbolId, val qualifiedName: String, truth: StubTruth?) {
    val indirectTruth: StubTruth? = truth?.copy(direct = false)
}

/**
 * The symbols that the analyzer will find in a stub, with the ids it derives from the stub syntax: the package as
 * namespaces, a type segment for each class, a term for each property and entry, and a method segment with the simple
 * names of the parameter types for each function and constructor. The identities name the source set of the stub.
 */
internal class StubSymbols(
    private val identity: IdentityFactory,
    private val generator: String,
    private val scope: Scope,
) {
    private val symbols = mutableListOf<Symbol>()
    private val relationships = mutableListOf<Relationship>()

    fun of(file: StubFile): StubFacts {
        val root = SymbolDescriptor().namespaces(file.packageName)
        file.classes.forEach { addClass(it, root, null, file.packageName) }
        return StubFacts(symbols.toList(), relationships.toList())
    }

    private fun addClass(stub: StubClass, parent: SymbolDescriptor, container: Owner?, parentName: String) {
        val name = if (stub.form == StubForm.COMPANION) COMPANION_NAME else stub.name
        val descriptor = parent.type(name)
        val owner = Owner(descriptor, identity.idOf(descriptor), qualify(parentName, name), stub.truth)
        val keyword = if (stub.form == StubForm.COMPANION) stub.form.keyword else "${stub.form.keyword} ${stub.name}"
        val signature = "public $keyword"
        add(
            Draft(
                owner.id,
                kindOf(stub.form),
                owner.qualifiedName,
                signature,
                container?.id,
                stub.truth,
                stub.certainty,
            ),
        )
        stub.constructor?.let { addConstructor(it, owner) }
        stub.entries.forEach { addEntry(it, owner) }
        stub.members.forEach { addMember(it, owner) }
        stub.nested.forEach { addClass(it, owner.descriptor, owner, owner.qualifiedName) }
    }

    private fun kindOf(form: StubForm): SymbolKind = when (form) {
        StubForm.CLASS, StubForm.DATA_CLASS -> classKind
        StubForm.ENUM_CLASS -> typeKind("kotlin.enum")
        StubForm.INTERFACE -> typeKind("kotlin.interface")
        StubForm.COMPANION -> typeKind("kotlin.object")
    }

    private fun addConstructor(parameters: List<StubParameter>, owner: Owner) {
        val descriptor = owner.descriptor.method(CONSTRUCTOR_NAME, StubTypes.disambiguator(parameters.map { it.type }))
        val signature = "public constructor(${parameters.joinToString(", ") { "${it.name}: ${it.type}" }})"
        val qualified = qualify(owner.qualifiedName, CONSTRUCTOR_NAME)
        add(Draft(identity.idOf(descriptor), constructorKind, qualified, signature, owner.id, owner.indirectTruth))
        parameters.filter { it.isProperty }.forEach { parameter ->
            val flags = if (parameter.isOverride) setOf(SymbolFlag.OVERRIDE) else emptySet()
            add(
                Draft(
                    id = identity.idOf(owner.descriptor.term(parameter.name)),
                    kind = propertyKind,
                    qualifiedName = qualify(owner.qualifiedName, parameter.name),
                    signature = "public val ${parameter.name}: ${parameter.type}",
                    container = owner.id,
                    truth = parameter.truth ?: owner.indirectTruth,
                    certainty = parameter.certainty,
                    flags = flags,
                    typeSource = parameter.typeSource,
                ),
            )
        }
    }

    private fun addEntry(entry: StubEntry, owner: Owner) {
        val id = identity.idOf(owner.descriptor.term(entry.name))
        val qualified = qualify(owner.qualifiedName, entry.name)
        add(Draft(id, entryKind, qualified, entry.name, owner.id, entry.truth ?: owner.indirectTruth))
    }

    private fun addMember(member: StubMember, owner: Owner) {
        val truth = member.truth ?: owner.indirectTruth
        val qualified = qualify(owner.qualifiedName, member.name)
        when (member) {
            is StubProperty -> {
                val id = identity.idOf(owner.descriptor.term(member.name))
                val signature = "public val ${member.name}: ${member.type}"
                val draft = Draft(id, propertyKind, qualified, signature, owner.id, truth, member.certainty)
                add(draft.copy(typeSource = member.typeSource))
            }

            is StubFunction -> {
                val types = member.parameters.map { it.type }
                val id = identity.idOf(owner.descriptor.method(member.name, StubTypes.disambiguator(types)))
                val draft = Draft(id, functionKind, qualified, signatureOf(member), owner.id, truth, member.certainty)
                add(draft.copy(typeSource = member.typeSource))
            }
        }
    }

    private fun signatureOf(function: StubFunction): String {
        val parameters = function.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
        return "public fun ${function.name}($parameters): ${function.returnType}"
    }

    private fun add(draft: Draft) {
        val truth = draft.truth
        symbols += Symbol(
            id = draft.id,
            kind = draft.kind,
            name = draft.qualifiedName.substringAfterLast('.'),
            qualifiedName = draft.qualifiedName,
            signature = draft.signature,
            container = draft.container,
            visibility = Visibility.PUBLIC,
            flags = draft.flags,
            origin = SymbolOrigin.Generated(generator, truth?.let { NodeRef.OfSymbol(it.source) }),
            declaration = null,
            nameLocation = null,
            documentation = null,
            scope = scope,
            evidenceClass = EvidenceClass.CONVENTION,
            certainty = draft.certainty,
            attributes = draft.typeSource?.let { mapOf(TYPE_SOURCE_ATTRIBUTE to it.value) }.orEmpty(),
        )
        if (truth != null && truth.direct) relationships += generates(truth.source, draft)
    }

    private fun generates(source: SymbolId, generated: Draft) = Relationship(
        from = NodeRef.OfSymbol(source),
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(generated.id), generated.certainty),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = scope,
    )

    private fun qualify(parent: String, name: String) = if (parent.isEmpty()) name else "$parent.$name"
}
