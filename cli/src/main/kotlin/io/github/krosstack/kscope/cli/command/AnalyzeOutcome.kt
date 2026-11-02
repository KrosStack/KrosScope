package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.text.AnalyzeText
import io.github.krosstack.kscope.cli.text.UnitCard
import io.github.krosstack.kscope.cli.text.UnitCardText
import io.github.krosstack.kscope.cli.text.UnitCards
import io.github.krosstack.kscope.cli.text.UnitFacts
import io.github.krosstack.kscope.cli.text.format.Readings
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.service.ArtifactKind
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.artifactKinds
import io.github.krosstack.kscope.service.declarationsNamed
import io.github.krosstack.kscope.service.neighbourhood
import io.github.krosstack.kscope.service.resolveNode
import io.github.krosstack.kscope.service.workspaceLocations

/** What `analyze` found out for its argument, with the first line that says how the argument was read. */
internal sealed interface AnalyzeOutcome {
    val locations: WorkspaceLocations

    fun render(text: String, context: TextContext): String

    class Task(
        private val result: AnalysisResult,
        private val kinds: Map<ArtifactRef, ArtifactKind>,
        private val reading: String,
        override val locations: WorkspaceLocations,
    ) : AnalyzeOutcome {
        override fun render(text: String, context: TextContext): String =
            AnalyzeText.render(result, context, kinds, reading)
    }

    class Card(private val card: UnitCard, private val reading: String, override val locations: WorkspaceLocations) :
        AnalyzeOutcome {
        override fun render(text: String, context: TextContext): String =
            UnitCardText.render(card, text, context, reading)
    }

    data object NoUnit : AnalyzeOutcome {
        override val locations: WorkspaceLocations = WorkspaceLocations.SINGLE

        override fun render(text: String, context: TextContext): String =
            UnitCardText.missing(text, context, Readings.NAME_REQUESTED)
    }
}

/** Asks the service what [routing] calls for: a card for a name or file, otherwise a task analysis. */
internal fun KrosScope.analyzeAs(text: String, routing: Routing): AnalyzeOutcome {
    if (routing.reading == Reading.TASK) {
        if (!routing.requested) rejectPathWithoutDirectory(text)
        return analyzeTask(text, Readings.TASK)
    }
    val card = unitCard(routing.argument)
    return when {
        card != null -> AnalyzeOutcome.Card(card, readingOf(card, text), workspaceLocations())
        routing.requested -> AnalyzeOutcome.NoUnit
        else -> analyzeTask(text, Readings.taskAfterMiss(text))
    }
}

private fun KrosScope.rejectPathWithoutDirectory(text: String) {
    val token = text.trim()
    if (token.none(Char::isWhitespace)) resolveNode(token)
}

private fun readingOf(card: UnitCard, text: String): String =
    if (card.kind == UnitCards.FILE_KIND) Readings.file(text) else Readings.name(text)

private fun KrosScope.analyzeTask(text: String, reading: String): AnalyzeOutcome {
    val result = analyze(text)
    return AnalyzeOutcome.Task(result, artifactKinds(AnalyzeText.relevantFiles(result)), reading, workspaceLocations())
}

private fun KrosScope.unitCard(argument: String): UnitCard? {
    val resolution = resolveNode(argument)
    val node = resolution.candidates.first()
    val impact = impact(node)
    val neighbourhood = neighbourhood(node)
    val isUnit = neighbourhood.symbol != null || impact.roots.any { it is NodeRef.OfArtifact }
    if (!isUnit) return null
    val others = resolution.candidates.drop(1)
    val byId = if (others.isEmpty()) emptyMap() else symbolsById(argument, others)
    return UnitCards.of(UnitFacts(node, neighbourhood, impact, others.mapNotNull { byId[it] }, workspaceLocations()))
}

private fun KrosScope.symbolsById(argument: String, others: List<String>): Map<String, Symbol> {
    val named = declarationsNamed(argument).associateBy { it.id.value }
    if (others.all { it in named }) return named
    return symbol(argument).symbols.associate { it.symbol.id.value to it.symbol }
}
