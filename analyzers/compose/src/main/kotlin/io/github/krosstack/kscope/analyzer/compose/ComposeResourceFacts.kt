package io.github.krosstack.kscope.analyzer.compose

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
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

private const val QUALIFIERS_ATTRIBUTE = "compose.resource.qualifiers"
private const val DEFINITION = "definition"
private val fileStart = TextRange(1, 1, 1, 1)

/**
 * The facts of one resource file. Every resource it defines is a symbol; when several files define the same resource,
 * the store keeps one of the symbols. Each definition site beyond the first one in the default configuration is a
 * `references/definition` relationship from the file.
 */
internal class ComposeResourceFacts(
    private val artifact: Artifact,
    private val location: ComposeResourceLocation,
    private val module: Module,
    sourceSet: SourceSet,
) {
    private val scope = Scope(sourceSet = sourceSet.id)
    private val sourceSetName = sourceSet.symbolName()

    fun of(parsed: ParsedResourceFile): List<Fact> =
        parsed.definitions.distinctBy { it.key }.map { Fact.OfSymbol(symbolOf(it)) } +
            definitionSites(parsed.definitions).map { Fact.OfRelationship(definitionOf(it)) } +
            listOfNotNull(parsed.problem?.let(::malformed))

    private fun definitionSites(definitions: List<ResourceDefinition>): List<ResourceDefinition> {
        val firstOfKey = definitions.withIndex().groupBy({ it.value.key }, { it.index }).mapValues { it.value.first() }
        return definitions.withIndex()
            .filter { (index, definition) -> !location.isDefaultConfiguration || firstOfKey[definition.key] != index }
            .map { it.value }
    }

    private fun malformed(problem: String) = Fact.OfDiagnostic(
        Diagnostic(
            severity = DiagnosticSeverity.WARNING,
            code = ReasonCode("compose.resource.xml-malformed"),
            message = "${artifact.ref.path.value}: the file could not be read to its end: $problem.",
            location = locationOf(fileStart),
        ),
    )

    private fun definitionOf(definition: ResourceDefinition) = Relationship(
        from = NodeRef.OfArtifact(artifact.ref),
        kind = RelationKind.REFERENCES,
        subKind = DEFINITION,
        resolution = Resolution.Resolved(
            NodeRef.OfSymbol(ComposeResourceSymbols.idOf(module.id, sourceSetName, definition.key)),
            Certainty.EXACT,
        ),
        evidenceClass = EvidenceClass.STRUCTURAL,
        site = locationOf(definition.nameRange),
        scope = scope,
    )

    private fun symbolOf(definition: ResourceDefinition): Symbol {
        val key = definition.key
        return Symbol(
            id = ComposeResourceSymbols.idOf(module.id, sourceSetName, key),
            kind = ComposeResourceSymbols.kindOf(key.type),
            name = key.name,
            qualifiedName = null,
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

    // The suffix of a definition in another configuration sorts after the plain signature of the default one, so the
    // store shows the default definition of a resource that several files define.
    private fun signatureOf(key: ResourceKey): String =
        "${key.type.id} ${key.name}" + if (location.isDefaultConfiguration) "" else "(${location.folderName})"

    private fun locationOf(range: TextRange) = SourceLocation(artifact.ref, range)
}
