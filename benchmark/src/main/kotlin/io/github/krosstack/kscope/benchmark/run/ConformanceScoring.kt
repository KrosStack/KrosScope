package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.gold.GoldSet
import io.github.krosstack.kscope.benchmark.gold.GoldValidator
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.CoverageSummarizer
import io.github.krosstack.kscope.benchmark.score.RelationshipClassScorer
import io.github.krosstack.kscope.benchmark.score.Score
import io.github.krosstack.kscope.benchmark.score.build.BuildModelScorer
import io.github.krosstack.kscope.benchmark.score.code.ClosedWorldScorer
import io.github.krosstack.kscope.benchmark.score.code.DeclarationScorer
import io.github.krosstack.kscope.spi.graph.GraphReader

/** Scores a graph against gold: build model, declarations, closed-world edges, relationship classes and coverage. */
public class ConformanceScoring(reader: GraphReader, private val gold: GoldSet) {
    private val index = GraphIndex(reader)
    private val resolver = GoldAddressResolver(index)

    public fun score(mode: ScanMode, scan: ScanMetrics): ConformanceResult {
        val declarations = DeclarationScorer(index)
        val closedWorld = ClosedWorldScorer(index, resolver)
        val files = gold.closedWorld.map(declarations::score)
        val lineCounts = index.artifacts.associate { it.ref.path.value to it.lineCount }
        return ConformanceResult(
            mode = mode.label,
            level = mode.level.wireName,
            scan = scan,
            coverage = CoverageSummarizer(index).summarize(),
            buildModel = BuildModelScorer(index).score(gold.buildModel),
            declarations = DeclarationMetrics(Score.combine(files.map { it.score }), files),
            closedWorld = ClosedWorldMetrics(gold.closedWorld.map(closedWorld::score)),
            relationshipClasses = RelationshipClassScorer(index, resolver).score(gold.relationships),
            goldProblems = GoldValidator(lineCounts).problems(gold),
        )
    }
}
