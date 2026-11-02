package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlConstant
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlDeclaration
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlInterface
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlMethod
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlName
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParcelable
import io.github.krosstack.kscope.analyzer.android.resources.symbolName
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

internal const val ONEWAY_ATTRIBUTE = "aidl.oneway"
internal const val STRUCTURED_ATTRIBUTE = "aidl.structured"

private data class SymbolDraft(
    val id: SymbolId,
    val kind: SymbolKind,
    val name: AidlName,
    val qualifiedName: String,
    val signature: String,
    val container: SymbolId?,
    val declaration: TextRange,
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * The symbols of one `.aidl` file: an interface with its methods and constants, or a parcelable with its constants.
 * The ids are the ones that the surface synthesizer names as the source of its generated symbols.
 */
internal class AidlFacts(private val artifact: Artifact, private val module: Module, sourceSet: SourceSet) {
    private val scope = Scope(sourceSet = sourceSet.id)
    private val sourceSetName = sourceSet.symbolName()

    fun of(file: AidlFile): List<Fact> =
        file.declarations.flatMap { declaration -> symbolsOf(file, declaration).map { Fact.OfSymbol(symbolOf(it)) } }

    private fun symbolsOf(file: AidlFile, declaration: AidlDeclaration): List<SymbolDraft> = when (declaration) {
        is AidlInterface -> interfaceDrafts(file, declaration)
        is AidlParcelable -> parcelableDrafts(file, declaration)
    }

    private fun interfaceDrafts(file: AidlFile, declaration: AidlInterface): List<SymbolDraft> {
        val descriptor = AidlSymbols.typeDescriptor(file.packageOf(declaration), declaration.name.text)
        val id = idOf(descriptor)
        val qualifiedName = file.qualifiedNameOf(declaration)
        val oneway = if (declaration.isOneway) mapOf(ONEWAY_ATTRIBUTE to "true") else emptyMap()
        val own = SymbolDraft(
            id = id,
            kind = AidlSymbols.interfaceKind,
            name = declaration.name,
            qualifiedName = qualifiedName,
            signature = declaration.signature(),
            container = null,
            declaration = declaration.declaration,
            attributes = oneway,
        )
        val methods = declaration.methods.map { methodDraft(declaration, it, descriptor, id, qualifiedName) }
        return listOf(own) + methods + constantDrafts(declaration.constants, descriptor, id, qualifiedName)
    }

    private fun methodDraft(
        owner: AidlInterface,
        method: AidlMethod,
        ownerDescriptor: SymbolDescriptor,
        ownerId: SymbolId,
        ownerName: String,
    ): SymbolDraft {
        val isOneway = owner.isOneway || method.isOneway
        return SymbolDraft(
            id = idOf(AidlSymbols.methodDescriptor(ownerDescriptor, method)),
            kind = AidlSymbols.methodKind,
            name = method.name,
            qualifiedName = "$ownerName.${method.name.text}",
            signature = method.signature(),
            container = ownerId,
            declaration = method.declaration,
            attributes = if (isOneway) mapOf(ONEWAY_ATTRIBUTE to "true") else emptyMap(),
        )
    }

    private fun parcelableDrafts(file: AidlFile, declaration: AidlParcelable): List<SymbolDraft> {
        val descriptor = AidlSymbols.typeDescriptor(file.packageOf(declaration), declaration.name.text)
        val id = idOf(descriptor)
        val qualifiedName = file.qualifiedNameOf(declaration)
        val own = SymbolDraft(
            id = id,
            kind = AidlSymbols.parcelableKind,
            name = declaration.name,
            qualifiedName = qualifiedName,
            signature = declaration.signature(),
            container = null,
            declaration = declaration.declaration,
            attributes = mapOf(STRUCTURED_ATTRIBUTE to declaration.isStructured.toString()),
        )
        return listOf(own) + constantDrafts(declaration.constants, descriptor, id, qualifiedName)
    }

    private fun constantDrafts(
        constants: List<AidlConstant>,
        ownerDescriptor: SymbolDescriptor,
        ownerId: SymbolId,
        ownerName: String,
    ): List<SymbolDraft> = constants.map { constant ->
        SymbolDraft(
            id = idOf(AidlSymbols.constantDescriptor(ownerDescriptor, constant)),
            kind = AidlSymbols.constantKind,
            name = constant.name,
            qualifiedName = "$ownerName.${constant.name.text}",
            signature = constant.signature(),
            container = ownerId,
            declaration = constant.declaration,
        )
    }

    private fun idOf(descriptor: SymbolDescriptor): SymbolId = AidlSymbols.idOf(module.id, sourceSetName, descriptor)

    private fun symbolOf(draft: SymbolDraft) = Symbol(
        id = draft.id,
        kind = draft.kind,
        name = draft.name.text,
        qualifiedName = draft.qualifiedName,
        signature = draft.signature,
        container = draft.container,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Source,
        declaration = SourceLocation(artifact.ref, draft.declaration),
        nameLocation = SourceLocation(artifact.ref, draft.name.range),
        documentation = null,
        scope = scope,
        evidenceClass = EvidenceClass.STRUCTURAL,
        certainty = Certainty.EXACT,
        attributes = draft.attributes,
    )
}
