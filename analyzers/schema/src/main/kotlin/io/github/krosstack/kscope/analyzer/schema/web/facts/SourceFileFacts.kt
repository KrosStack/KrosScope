package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol

/** The symbol of each hand-written TypeScript or JavaScript file, the end of the links from the files built from it. */
internal class SourceFileFacts(private val view: RepositoryView) {
    fun of(source: Artifact): List<Fact> {
        val text = view.textOf(source)
        val placement = view.placementOf(source)
        return when {
            text == null -> listOf(WebNotes.unreadable(source))
            placement == null -> listOf(WebNotes.moduleUnknown(source))
            else -> listOf(Fact.OfSymbol(symbolOf(source, text, placement)))
        }
    }

    private fun symbolOf(source: Artifact, text: String, placement: Placement): Symbol =
        FileSymbols.of(source, text, placement, WebKinds.sourceFileKind(source.type), EvidenceClass.STRUCTURAL)
}
