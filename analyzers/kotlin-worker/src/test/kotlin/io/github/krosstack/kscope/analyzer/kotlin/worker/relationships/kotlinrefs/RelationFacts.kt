package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationSamples
import io.github.krosstack.kscope.model.FactBatch

/** Analyses a sample once for all the tests that look at it. The samples are never modified, so sharing is safe. */
object RelationFacts {
    val lang: List<FactBatch> by lazy { analyzeAll(RelationSamples.lang) }
    val langEdges: List<String> by lazy { Edges("lang@main ").of(lang) }
    val langLocatedEdges: List<String> by lazy { Edges("lang@main ").located(lang) }

    val mixed: List<FactBatch> by lazy { analyzeAll(RelationSamples.mixed) }
    val mixedEdges: List<String> by lazy { Edges("mixed@main ").of(mixed) }
}
