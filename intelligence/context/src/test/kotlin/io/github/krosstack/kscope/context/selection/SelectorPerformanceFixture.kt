package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel

internal class SelectorPerformanceFixture(fileCount: Int, declarationsPerFile: Int, generated: Boolean = false) {
    private val text = (0 until declarationsPerFile).map { "fun member$it(): String = \"α🙂漢字 $it\"" }
        .joinToString("\n", postfix = "\n")
    private val files = (0 until fileCount).map { sourceArtifact("source/Group$it", text) }
    private val symbols = files.flatMapIndexed { fileIndex, file ->
        text.lines().filter(String::isNotEmpty).mapIndexed { lineIndex, line ->
            declaration(
                "group${fileIndex}Member$lineIndex",
                file,
                TextRange(lineIndex + 1, 1, lineIndex + 1, line.length + 1),
                "fun member$lineIndex(): String",
            )
        }
    }
    private val surfaces = if (generated) {
        symbols.map { symbol ->
            symbol.copy(
                id = SymbolId(symbol.id.value + "generated"),
                name = symbol.name + "Generated",
                signature = symbol.signature + " generated",
                origin = SymbolOrigin.Generated("test.generator", NodeRef.OfSymbol(symbol.id)),
                declaration = null,
                nameLocation = null,
            )
        }
    } else {
        emptyList()
    }
    private val content = files.associate { it.ref to text }
    val fixture = ContextFixture(files, symbols + surfaces, content)
    val graph = ContextGraph(fixture)
    val estimator = TokenEstimators.create()
    val input = ContextInput(
        "inspect sample code",
        files.map { ContextCandidate(NodeRef.OfArtifact(it.ref), 1.0) } +
            symbols.map { ContextCandidate(NodeRef.OfSymbol(it.id), 1.0 / declarationsPerFile) } +
            surfaces.map { ContextCandidate(NodeRef.OfSymbol(it.id), 1.0 / declarationsPerFile) },
    )

    fun offers(level: ContextLevel = ContextLevel.RECOMMENDED): List<ContextOffer> {
        val renderer = FragmentRenderer(graph, SourceDocuments(fixture), TokenCosts(estimator))
        return ContextPlanner(graph).plan(input, level).candidates.flatMap { renderer.render(it, level).offers }
    }

    fun merge(): FragmentMerge = FragmentMerge(graph, SourceDocuments(fixture), TokenCosts(estimator))
}
