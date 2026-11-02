package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.gold.GoldBuildModel
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.Score
import kotlinx.serialization.Serializable

@Serializable
public data class DimensionScore(public val name: String, public val score: Score)

/** The agreement of the modelled build with gold, per dimension and over all of them. */
@Serializable
public data class BuildModelScore(public val dimensions: List<DimensionScore>, public val overall: Score)

/** One kind of fact about the build: what gold lists and what the graph holds, as comparable items. */
internal class Dimension(val name: String, val expected: List<Item>, val produced: List<Item>)

/**
 * Compares the modules, plugins, namespaces, source sets with their roots and refinements, targets, variants, project
 * dependencies and version catalog entries of the graph with gold. The modules of included builds (build logic) are
 * not compared with the modules of gold, which lists them only as included builds; they are checked as such.
 */
internal class BuildModelScorer(index: GraphIndex) {
    private val sources = listOf(
        ModuleDimensions(index),
        PluginDimensions(index),
        TargetDimensions(index),
        SourceSetDimensions(index),
        DependencyDimensions(index),
    )

    fun score(gold: GoldBuildModel): BuildModelScore {
        val dimensions = sources.flatMap { it.dimensions(gold) }.map {
            DimensionScore(it.name, Matching.of(it.expected, it.produced).score())
        }
        return BuildModelScore(dimensions, Score.combine(dimensions.map { it.score }))
    }
}

/** Produces the dimensions of one theme of the build model. */
internal interface DimensionSource {
    fun dimensions(gold: GoldBuildModel): List<Dimension>
}

internal fun fact(module: String, detail: String) = Item("$module|$detail", "$module: $detail")
