package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.service.contextInput

internal object SemanticContextCandidates {
    fun input(result: AnalysisResult, baseline: AnalysisResult, symbolFile: (SymbolId) -> ArtifactRef?): ContextInput {
        require(baseline.query.text == result.query.text) { "Structural baseline must have the same query text." }
        val structural = baseline.contextInput()
        return StructuralContextTail.input(head(result, symbolFile), structural, result.files.minOfOrNull { it.score })
    }

    private fun head(result: AnalysisResult, symbolFile: (SymbolId) -> ArtifactRef?): ContextInput {
        val files = result.files.associateBy { it.artifact }
        val primary = result.files.map {
            ContextCandidate(NodeRef.OfArtifact(it.artifact), it.score, role(it.role))
        }
        val located = result.symbols.filter { it.score.isFinite() && it.score > 0.0 }.mapNotNull { ranked ->
            symbolFile(ranked.symbol.id)?.takeIf { it in files }?.let { it to ranked }
        }
        val peaks = located.groupBy({ it.first }, { it.second.score }).mapValues { it.value.max() }
        val symbols = located.map { (artifact, ranked) ->
            val file = files.getValue(artifact)
            ContextCandidate(
                NodeRef.OfSymbol(ranked.symbol.id),
                file.score * (ranked.score / peaks.getValue(artifact)),
                role(file.role),
            )
        }
        val primaryPeak = primary.maxOfOrNull { it.relevance } ?: 1.0
        val testPeak = result.tests.maxOfOrNull { it.score } ?: 0.0
        val tests = result.tests.map {
            val relevance = if (testPeak > 0.0) it.score / testPeak * primaryPeak else 0.0
            ContextCandidate(NodeRef.OfArtifact(it.artifact), relevance, ContextRole.TEST)
        }
        return ContextInput(
            result.query.text,
            primary + symbols + tests,
            result.boundaries,
            result.tests.map { NodeRef.OfArtifact(it.artifact) },
            result.unresolved,
        )
    }

    private fun role(role: FileRole): ContextRole = if (role == FileRole.EDIT) ContextRole.EDIT else ContextRole.READ
}
