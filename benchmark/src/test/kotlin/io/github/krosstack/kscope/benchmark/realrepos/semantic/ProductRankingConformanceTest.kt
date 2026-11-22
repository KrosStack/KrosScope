package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductRankingConformanceTest {
    @Test
    fun `product final file lists replay the frozen DEV originals`() = replay("original", "dev-replay-001")

    @Test
    fun `product final file lists replay the frozen DEV paraphrases`() = replay("paraphrase", "dev-replay-002")

    private fun replay(kind: String, id: String) {
        val cases = requireNotNull(javaClass.getResource("/semantic/dev-fusion-v1.jsonl")).readText()
            .lineSequence().filter { it.isNotBlank() }
            .map { KscopeJson.decodeFromString<FusionReplayCase>(it) }.toList()
        assertEquals(listOf("dev-replay-001", "dev-replay-002"), cases.map { it.id })
        val case = cases.single { it.queryKind == kind }
        assertEquals(id, case.id)
        assertEquals(160, case.structural.size)
        assertEquals(174, case.semantic.size)
        assertTrue(case.structural.size > 10 && case.semantic.size > 10)
        val graph = ReplayGraph(case.structural + case.semantic)
        val fused = FileRankFusion.fuse(case.structural.map(graph::ref), case.semantic.map(graph::ref))
        assertEquals(case.expectedFusedTop10, fused.take(10).map { it.artifact.path.value })
        val retrieval = TaskRetrieval(graph, graph)
        val baseline = retrieval.analyze("original test query").copy(
            files = case.structural.mapIndexed { index, path ->
                val score = 1.0 / (index + 1)
                RankedFile(graph.ref(path), score, FileRole.EDIT, listOf(SignalContribution(SignalKind.GRAPH, score)))
            },
        )
        val actual = retrieval.withFileRanking(baseline, fused)
        assertEquals(10, actual.files.size)
        assertEquals(case.expectedFusedTop10, actual.files.map { it.artifact.path.value })
    }
}
