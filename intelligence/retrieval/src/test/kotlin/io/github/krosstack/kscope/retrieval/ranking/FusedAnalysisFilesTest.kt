package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RetrievalIndex
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.artifact
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FusedAnalysisFilesTest {
    private val file = artifact("src/First.kt").ref

    @Test
    fun `RRF reasons sum to fused scores without changing structural attribution`() {
        val structural = RankedFile(
            file,
            8.0,
            FileRole.EDIT,
            listOf(SignalContribution(SignalKind.LEXICAL_ARTIFACT, 6.0), SignalContribution(SignalKind.GRAPH, 2.0)),
        )
        val fused = FileRankFusion.fuse(listOf(file), listOf(file))
        val actual = FusedAnalysisFiles.build(listOf(structural), fused).single()
        assertEquals(2.0 / 61, actual.score)
        assertEquals(listOf(0.75 / 61, 0.25 / 61, 1.0 / 61), actual.reasons.map { it.score })
        assertEquals(actual.score, actual.reasons.sumOf { it.score }, 1e-15)
        assertEquals(SignalKind.SEMANTIC, actual.reasons.last().signal)
    }

    @Test
    fun `semantic only files remain read candidates`() {
        val actual = FusedAnalysisFiles.build(emptyList(), FileRankFusion.fuse(emptyList(), listOf(file))).single()
        assertEquals(FileRole.READ, actual.role)
        assertEquals(listOf(SignalContribution(SignalKind.SEMANTIC, 1.0 / 61)), actual.reasons)
    }

    @Test
    fun `surviving structural edit roles remain unchanged`() {
        val original = listOf(
            RankedFile(file, 4.0, FileRole.EDIT, listOf(SignalContribution(SignalKind.GRAPH, 4.0))),
            RankedFile(artifact("src/Second.kt").ref, 1.0, FileRole.READ, emptyList()),
        )
        val fused = FileRankFusion.fuse(original.map { it.artifact }, listOf(original.last().artifact))
        val actual = FusedAnalysisFiles.build(original, fused)
        assertEquals(fused.map { it.artifact }, actual.map { it.artifact })
        assertEquals(original.associate { it.artifact to it.role }, actual.associate { it.artifact to it.role })
    }

    @Test
    fun `symbol filtering uses the canonical projected source without changing symbol evidence`() {
        val source = artifact("src/Original.kt")
        val generated = artifact("generated/Accessor.kt")
        val projected = retrievalSymbol(
            "Accessor",
            generated,
            origin = SymbolOrigin.Generated("test", NodeRef.OfArtifact(source.ref)),
        )
        val excluded = retrievalSymbol("Excluded", generated)
        val graph = SemanticFixture(listOf(source, generated), listOf(projected, excluded))
        val retrieval = TaskRetrieval(graph, RetrievalIndex())
        val symbol = RankedSymbol(projected, 100.0, listOf(SignalContribution(SignalKind.GRAPH, 100.0)))
        val baseline = retrieval.analyze("query").copy(symbols = listOf(symbol, RankedSymbol(excluded, 50.0)))
        val result = retrieval.withFileRanking(baseline, FileRankFusion.fuse(emptyList(), listOf(source.ref)))
        assertEquals(listOf(symbol), result.symbols)
    }
}
