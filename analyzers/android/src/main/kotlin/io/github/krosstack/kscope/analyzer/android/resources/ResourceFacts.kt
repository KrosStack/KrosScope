package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.analyzer.android.ResourceLocation
import io.github.krosstack.kscope.analyzer.android.linking.ClassReferenceFacts
import io.github.krosstack.kscope.analyzer.android.linking.OwnedMention
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

private const val QUALIFIERS_ATTRIBUTE = "android.resource.qualifiers"
private const val DEFINITION = "definition"
private val FILE_START = TextRange(1, 1, 1, 1)

/**
 * The facts of one resource file. Every resource it defines is a symbol; when several files define the same resource,
 * the store keeps one of the symbols. Each definition site beyond the first one in the default configuration is a
 * `references/definition` relationship from the file. References that need another file to be looked up are symbolic
 * references for the linker.
 */
internal class ResourceFacts(
    private val artifact: Artifact,
    private val location: ResourceLocation,
    private val module: Module,
    sourceSet: SourceSet,
) {
    private val scope = Scope(sourceSet = sourceSet.id)
    private val sourceSetName = sourceSet.symbolName()
    private val fileKey = ResourceKey(location.type, location.baseName)

    fun of(parsed: ParsedResourceFile): List<Fact> =
        parsed.definitions.distinctBy { it.key }.map { Fact.OfSymbol(symbolOf(it)) } +
            definitionSites(parsed) + references(parsed) + classReferences(parsed) + notes(parsed)

    private fun definitionSites(parsed: ParsedResourceFile): List<Fact> {
        val definitions = parsed.definitions
        val firstOfKey = definitions.withIndex().groupBy({ it.value.key }, { it.index }).mapValues { it.value.first() }
        return definitions.withIndex()
            .filter { (index, definition) -> !location.isDefaultConfiguration || firstOfKey[definition.key] != index }
            .map { (_, definition) -> Fact.OfRelationship(definitionOf(definition)) }
    }

    private fun references(parsed: ParsedResourceFile): List<Fact> =
        parsed.uses.map(::referenceOf).distinct().map { Fact.OfSymbolicReference(it) }

    private fun classReferences(parsed: ParsedResourceFile): List<Fact> {
        val owner = NodeRef.OfSymbol(ResourceSymbols.idOf(module.id, sourceSetName, fileKey))
        val mentions = parsed.classMentions.map { OwnedMention(owner, it) }
        return ClassReferenceFacts(artifact, module.androidNamespace, scope).of(mentions)
    }

    private fun referenceOf(use: ResourceUse) = SymbolicReference(
        from = NodeRef.OfSymbol(ResourceSymbols.idOf(module.id, sourceSetName, use.owner)),
        kind = RelationKind.REFERENCES,
        subKind = use.subKind,
        namespace = ResourceSymbols.NAMESPACE,
        key = use.target.reference,
        site = locationOf(use.range),
        scope = scope,
        evidenceClass = EvidenceClass.TEXTUAL,
    )

    private fun notes(parsed: ParsedResourceFile): List<Fact> = listOfNotNull(
        parsed.externalReferences.takeIf { it > 0 }?.let {
            note(DiagnosticSeverity.INFO, "android.resource.external-references") {
                "$it references to framework or other packages' resources are not linked"
            }
        },
        parsed.problem?.let { problem ->
            note(DiagnosticSeverity.WARNING, "android.resource.xml-malformed") {
                "the file could not be read to its end: $problem"
            }
        },
    )

    private fun note(severity: DiagnosticSeverity, code: String, message: () -> String) = Fact.OfDiagnostic(
        Diagnostic(severity, ReasonCode(code), "${artifact.ref.path.value}: ${message()}.", locationOf(FILE_START)),
    )

    private fun definitionOf(definition: ResourceDefinition) = Relationship(
        from = NodeRef.OfArtifact(artifact.ref),
        kind = RelationKind.REFERENCES,
        subKind = DEFINITION,
        resolution = Resolution.Resolved(
            NodeRef.OfSymbol(ResourceSymbols.idOf(module.id, sourceSetName, definition.key)),
            Certainty.EXACT,
        ),
        evidenceClass = EvidenceClass.STRUCTURAL,
        site = locationOf(definition.nameRange),
        scope = scope,
    )

    private fun symbolOf(definition: ResourceDefinition): Symbol {
        val key = definition.key
        return Symbol(
            id = ResourceSymbols.idOf(module.id, sourceSetName, key),
            kind = ResourceSymbols.kindOf(key.type),
            name = key.name,
            qualifiedName = module.androidNamespace?.let { "$it.R.${key.type}.${key.fieldName}" },
            signature = signatureOf(key),
            container = null,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = locationOf(definition.declaration),
            nameLocation = locationOf(definition.nameRange),
            documentation = null,
            scope = scope,
            evidenceClass = EvidenceClass.STRUCTURAL,
            certainty = Certainty.EXACT,
            attributes = location.qualifiers?.let { mapOf(QUALIFIERS_ATTRIBUTE to it) }.orEmpty(),
        )
    }

    // The store shows the canonically first of several symbols with one id. The suffix of a definition in another
    // configuration sorts after the plain signature of the default one, so the default definition is the one shown.
    private fun signatureOf(key: ResourceKey): String =
        "${key.type} ${key.name}" + if (location.isDefaultConfiguration) "" else "(${location.folder})"

    private fun locationOf(range: TextRange) = SourceLocation(artifact.ref, range)
}
