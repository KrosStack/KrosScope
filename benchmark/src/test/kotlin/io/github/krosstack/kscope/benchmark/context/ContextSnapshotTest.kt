package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpus
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ContextSnapshotTest {
    private val artifact = Graphs.artifact("source")
    private val symbol = Graphs.symbol("source", "example.Work", "kotlin.function", 1)
    private val corpus = LocalizationCorpus(mapOf("source" to "actual content"), LocalizationCorpusSummary(1))

    @Test
    fun `semantic changes invalidate a checkpoint even when indexed artifact hashes are identical`() {
        val original = FakeGraph(symbols = listOf(symbol), artifacts = listOf(artifact))
        val symbolChanged = FakeGraph(
            symbols = listOf(symbol.copy(signature = "changed signature")),
            artifacts = listOf(artifact),
        )
        val edgeChanged = FakeGraph(
            symbols = listOf(symbol),
            artifacts = listOf(artifact),
            relationships = listOf(Graphs.call(symbol, symbol, "source", 1)),
        )
        val root = Graphs.buildRoot("")
        val buildChanged = FakeGraph(
            symbols = listOf(symbol),
            artifacts = listOf(artifact),
            modules = listOf(Graphs.module(":core", "", root.id)),
            buildRoots = listOf(root),
        )

        val baseline = ContextSnapshot.fingerprint(original, corpus)
        assertEquals(baseline, ContextSnapshot.fingerprint(original, corpus))
        for (changed in listOf(symbolChanged, edgeChanged, buildChanged, withHistory(original))) {
            assertNotEquals(baseline, ContextSnapshot.fingerprint(changed, corpus))
        }
    }

    @Test
    fun `loaded text and corpus readability invalidate unchanged graph artifacts`() {
        val graph = FakeGraph(symbols = listOf(symbol), artifacts = listOf(artifact))
        val baseline = ContextSnapshot.fingerprint(graph, corpus)
        val changedText = corpus.copy(documents = mapOf("source" to "different actual content"))
        val unreadable = LocalizationCorpus(emptyMap(), LocalizationCorpusSummary(unreadableFiles = 1))

        assertNotEquals(baseline, ContextSnapshot.fingerprint(graph, changedText))
        assertNotEquals(baseline, ContextSnapshot.fingerprint(graph, unreadable))
    }

    private fun withHistory(graph: GraphReader): GraphReader = object : GraphReader by graph {
        override fun coverage(): CoverageReport = graph.coverage().copy(metricsByName = mapOf("history.commits" to 1L))
        override fun metrics(name: String): List<Metric> =
            listOf(Metric(NodeRef.OfArtifact(artifact.ref), "history.commits", 2.0))
    }
}
