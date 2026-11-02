package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolKind

/** The symbol of a file as a whole: named after the file, qualified by its path, and located at its first line. */
internal object FileSymbols {
    fun of(artifact: Artifact, text: String, placement: Placement, kind: SymbolKind, evidence: EvidenceClass): Symbol {
        val path = artifact.ref.path.value
        val firstLine = Span(0, text.lineSequence().first().length)
        return SchemaSymbol(
            id = WebIdentities.fileId(placement.identity, kind, path),
            kind = kind,
            name = path.substringAfterLast('/'),
            qualifiedName = path,
            signature = path,
            container = null,
            declaration = firstLine,
            nameSpan = firstLine,
            evidence = evidence,
        ).toSymbol(SymbolSite(artifact.ref, placement.scope, TextIndex(text)))
    }
}
