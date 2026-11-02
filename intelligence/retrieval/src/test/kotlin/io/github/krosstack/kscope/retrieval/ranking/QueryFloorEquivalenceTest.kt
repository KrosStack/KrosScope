package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.analysis.AnalysisFacts
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.relationship
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class QueryFloorEquivalenceTest {
    @Test
    fun `scores on arrays are bit for bit the scores of the map based iteration`() {
        repeat(SEEDS) { seed ->
            val graph = RetrievalGraph(randomGraph(Random(seed)))
            val random = Random(seed + SEEDS)
            repeat(SEED_SETS) {
                val seeds = linkedMapOf<NodeRef, Double>().also { chosen ->
                    repeat(random.nextInt(1, MAX_SEEDS)) {
                        chosen[randomNode(graph, random)] = random.nextDouble(0.1, 5.0)
                    }
                }

                val expected = FrozenGraphRanking(graph).rank(seeds)
                val actual = GraphRanking(graph).rank(seeds)

                assertEquals(expected.entries.toList(), actual.entries.toList(), "seed $seed")
            }
        }
    }

    @Test
    fun `the random graphs reach far enough to make the comparison mean something`() {
        val reached = (0 until SEEDS).map { seed ->
            val graph = RetrievalGraph(randomGraph(Random(seed)))
            GraphRanking(graph).rank(mapOf(randomNode(graph, Random(seed)) to 1.0)).size
        }

        assertTrue(reached.max() > MIN_REACH, "the largest neighbourhood has ${reached.max()} nodes")
    }

    @Test
    fun `nodes of files and the facts around them equal the full scans they replaced`() {
        repeat(SEEDS) { seed ->
            val graph = RetrievalGraph(randomGraph(Random(seed)))
            val frozen = FrozenAnalysisFacts(graph)
            val facts = AnalysisFacts(graph)
            val random = Random(seed)
            repeat(FILE_SETS) {
                val files = graph.artifacts.keys.shuffled(random).take(random.nextInt(1, MAX_FILES)).toSet()

                assertEquals(frozen.nodes(files).toList(), graph.nodes(files).toList(), "nodes, seed $seed")
                assertEquals(frozen.near(files), facts.near(files), "near, seed $seed")
                assertEquals(frozen.risks(files), facts.risks(files), "risks, seed $seed")
            }
        }
    }

    private fun allNodes(graph: RetrievalGraph): List<NodeRef> =
        graph.artifacts.keys.map { NodeRef.OfArtifact(it) } + graph.symbols.keys.map { NodeRef.OfSymbol(it) }

    private fun randomNode(graph: RetrievalGraph, random: Random): NodeRef = allNodes(graph).random(random)

    private fun randomGraph(random: Random): SemanticFixture {
        val files = List(FILES) { artifact("module${it % MODULES}/File$it.kt") }
        val symbols = files.flatMap { file ->
            val roots = List(random.nextInt(0, MAX_SYMBOLS)) { retrievalSymbol("${file.ref.path.value}#$it", file) }
            roots + roots.take(1).map { root -> retrievalSymbol("${root.name}.member", file, container = root.id) }
        }
        val nodes = files.map { NodeRef.OfArtifact(it.ref) } + symbols.map { NodeRef.OfSymbol(it.id) }
        val edges = List(EDGES) { randomEdge(nodes, random) }
        return SemanticFixture(files, symbols, edges)
    }

    private fun randomEdge(nodes: List<NodeRef>, random: Random): Relationship {
        val from = nodes.random(random)
        val kind = RelationKind.entries.random(random)
        return when (random.nextInt(RESOLUTIONS)) {
            0 -> relationship(from, nodes.random(random), kind, Certainty.entries.random(random))

            1 -> relationship(from, nodes.random(random), kind).copy(attributes = mapOf("confidence" to "0.5"))

            else -> relationship(from, from, kind).copy(
                resolution = Resolution.Unresolved("missing", ReasonCode("test.missing")),
            )
        }
    }

    private companion object {
        const val SEEDS = 25
        const val SEED_SETS = 4
        const val FILE_SETS = 6
        const val FILES = 40
        const val MODULES = 5
        const val MAX_SYMBOLS = 5
        const val MAX_SEEDS = 4
        const val MAX_FILES = 6
        const val EDGES = 220
        const val RESOLUTIONS = 4
        const val MIN_REACH = 40
    }
}
