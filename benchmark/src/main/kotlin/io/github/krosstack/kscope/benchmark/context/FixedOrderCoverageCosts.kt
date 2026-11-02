package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.math.ceil

internal class FixedOrderCoverageCosts(
    private val documents: Map<ArtifactRef, SourceDocument>,
    private val estimator: TokenEstimator,
    private val names: WorkspaceNames = WorkspaceNames.SINGLE,
) {
    fun measure(task: String, paths: List<String>, gold: Set<ArtifactRef>): FullFileCoverageCosts {
        if (gold.isEmpty()) return result(TokenCount(0), TokenCount(0))
        val filesByPath = documents.values.associateBy { names.of(it.artifact) }
        val ordered = paths.distinct().mapNotNull(filesByPath::get)
        val counts = ordered.runningFold(0) { count, document ->
            count + if (document.artifact in gold && document.text.isNotEmpty()) 1 else 0
        }.drop(1)
        val ninetyIndex = counts.indexOfFirst { it >= ceil(gold.size * COVERAGE_90).toInt() }.takeIf { it >= 0 }
        val fullIndex = counts.indexOfFirst { it == gold.size }.takeIf { it >= 0 }
        val ninety = cost(task, ordered, ninetyIndex)
        val full = if (fullIndex == ninetyIndex) ninety else cost(task, ordered, fullIndex)
        return result(ninety, full)
    }

    private fun result(ninety: TokenCount?, full: TokenCount?): FullFileCoverageCosts = FullFileCoverageCosts(
        ninety,
        full,
        reason(ninety),
        reason(full),
        measurement(ninety != null),
        measurement(full != null),
    )

    private fun reason(tokens: TokenCount?): String? = if (tokens == null) UNREACHABLE else null

    private fun measurement(reached: Boolean): CoverageCostMeasurement = if (reached) {
        CoverageCostMeasurement.EXACT_FIXED_ORDER
    } else {
        CoverageCostMeasurement.NOT_REACHED
    }

    private fun cost(task: String, ordered: List<SourceDocument>, index: Int?): TokenCount? = index?.let {
        estimator.count(ContextMarkdown.renderContents(task, ordered.take(it + 1).map(::fragment)))
    }

    private fun fragment(document: SourceDocument): ContextFragment = ContextFragment(
        document.artifact, document.fullRange(), NodeRef.OfArtifact(document.artifact), ContextRendering.FILE,
        ContextRole.READ, 1.0, estimator.count(document.text), document.text, true,
    )

    private companion object {
        const val COVERAGE_90 = 0.9
        const val UNREACHABLE = "The fixed ranking does not reach the required full source-file coverage."
    }
}
