package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

/** The file that a schema symbol is declared in, with the scope it holds in and the text its spans refer to. */
internal class SymbolSite(val artifact: ArtifactRef, val scope: Scope, val text: TextIndex) {
    fun location(span: Span): SourceLocation = SourceLocation(artifact, text.range(span))
}

/** A symbol declared in a source of truth such as a proto file, an SQLDelight file or a Room schema export. */
internal data class SchemaSymbol(
    val id: SymbolId,
    val kind: SymbolKind,
    val name: String,
    val qualifiedName: String,
    val signature: String,
    val container: SymbolId?,
    val declaration: Span,
    val nameSpan: Span,
    val documentation: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val evidence: EvidenceClass = EvidenceClass.STRUCTURAL,
) {
    fun toSymbol(site: SymbolSite): Symbol = Symbol(
        id = id,
        kind = kind,
        name = name,
        qualifiedName = qualifiedName,
        signature = signature,
        container = container,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Source,
        declaration = site.location(declaration),
        nameLocation = site.location(nameSpan),
        documentation = documentation,
        scope = site.scope,
        evidenceClass = evidence,
        certainty = Certainty.EXACT,
        attributes = attributes,
    )
}

private val FILE_START = TextRange(1, 1, 1, 1)

/** A diagnostic about the file as a whole, located at its start. */
internal fun fileNote(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String): Fact =
    Fact.OfDiagnostic(
        Diagnostic(
            severity,
            ReasonCode(code),
            "${artifact.ref.path.value}: $message.",
            SourceLocation(artifact.ref, FILE_START),
        ),
    )

/** The first sentence of a documentation comment, as the declaration analyzers keep it. */
internal fun firstSentence(documentation: String?): String? {
    val text = documentation?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val end = text.indexOf(". ").takeIf { it >= 0 }?.plus(1) ?: text.length
    return text.substring(0, end).replace(Regex("\\s+"), " ")
}
