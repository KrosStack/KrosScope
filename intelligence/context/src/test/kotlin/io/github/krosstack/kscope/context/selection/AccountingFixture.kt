package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class AccountingFixture(fileCount: Int, declarationsPerFile: Int) {
    private val texts = (0 until fileCount).map { file ->
        (0 until declarationsPerFile).joinToString("\n", postfix = "\n") { member ->
            "fun file${file}Member$member(): String = \"value $file $member α🙂\""
        }
    }
    private val files = texts.mapIndexed { index, text -> sourceArtifact("source/Part$index", text) }
    private val symbols = files.flatMapIndexed { fileIndex, file ->
        texts[fileIndex].lines().filter(String::isNotEmpty).mapIndexed { line, text ->
            declaration(
                "part${fileIndex}Member$line",
                file,
                TextRange(line + 1, 1, line + 1, text.length + 1),
                "fun file${fileIndex}Member$line(): String",
            )
        }
    }
    private val content = files.mapIndexed { index, file -> file.ref to texts[index] }.toMap()
    private val workspace = ContextFixture(files, symbols, content)
    val graph = ContextGraph(workspace)
    val input = ContextInput(
        "change the member values",
        files.mapIndexed { index, file -> fileCandidate(index, file.ref) } +
            symbols.mapIndexed { index, symbol -> ContextCandidate(NodeRef.OfSymbol(symbol.id), relevance(index) / 3) },
        unresolved = symbols.filterIndexed { index, _ -> index % 3 == 0 }.map(::unresolvedFrom),
    )

    fun offers(level: ContextLevel, estimator: TokenEstimator): List<ContextOffer> {
        val renderer = FragmentRenderer(graph, SourceDocuments(workspace), TokenCosts(estimator))
        return ContextPlanner(graph).plan(input, level).candidates.flatMap { renderer.render(it, level).offers }
    }

    fun merge(estimator: TokenEstimator): FragmentMerge =
        FragmentMerge(graph, SourceDocuments(workspace), TokenCosts(estimator))

    private fun fileCandidate(index: Int, ref: ArtifactRef): ContextCandidate {
        val role = if (index == 0) ContextRole.EDIT else ContextRole.READ
        return ContextCandidate(NodeRef.OfArtifact(ref), relevance(index), role)
    }

    private fun relevance(index: Int): Double = 1.0 / (1 + (index * 7) % 5)

    private fun unresolvedFrom(symbol: Symbol) = Relationship(
        from = NodeRef.OfSymbol(symbol.id),
        kind = RelationKind.CALLS,
        subKind = null,
        resolution = Resolution.Unresolved("missing", ReasonCode("test.missing")),
        evidenceClass = EvidenceClass.COMPILER,
        site = null,
        scope = Scope(),
    )
}
