package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import io.github.krosstack.kscope.semantic.search.SemanticFileRanking
import io.github.krosstack.kscope.semantic.store.GenerationFile
import kotlin.math.log2

internal object SemanticRanking {
    private const val LIMIT = 10

    fun semantic(generation: GenerationFile, vector: FloatArray): List<String> {
        val entries = generation.chunks.map { entry ->
            val artifacts = entry.chunk.nodes.filterIsInstance<NodeRef.OfArtifact>().map { it.ref } +
                entry.chunk.artifact
            SemanticFileRanking.Entry(entry.vector, artifacts.toSet())
        }
        return SemanticFileRanking.rank(entries, vector).map { it.path.value }.distinct()
    }

    fun files(scores: List<Pair<String, Double>>): List<String> = scores.groupBy({ it.first }, { it.second })
        .mapValues { it.value.max() }.entries.sortedWith(
            compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key },
        ).map { it.key }

    fun fuse(first: List<String>, second: List<String>): List<String> =
        FileRankFusion.fuse(first.map(::ref), second.map(::ref)).map { it.artifact.path.value }

    private fun ref(path: String) = ArtifactRef(RepositoryId("benchmark"), RepoPath(path))

    fun metrics(ranking: List<String>, goldFiles: List<String>): List<Double> {
        val gold = goldFiles.toSet()
        require(gold.isNotEmpty())
        val top = ranking.distinct().take(LIMIT)
        val hits = top.count { it in gold }
        val gain = top.mapIndexed { rank, path -> if (path in gold) 1.0 / log2(rank + 2.0) else 0.0 }.sum()
        val ideal = (0 until minOf(LIMIT, gold.size)).sumOf { 1.0 / log2(it + 2.0) }
        return listOf(if (hits > 0) 1.0 else 0.0, hits.toDouble() / gold.size, gain / ideal)
    }
}
