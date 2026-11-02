package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlinx.serialization.Serializable

/** How many relationships have an evidence class and resolution state, and their share of all relationships. */
@Serializable
public data class CoverageRow(
    public val evidence: String,
    public val state: String,
    public val count: Int,
    public val share: Double,
)

/**
 * Resolved targets that point into the workspace but name nothing the graph has. The count must be zero, because such
 * an edge would lead a consumer nowhere. [examples] holds the first few.
 */
@Serializable
public data class IntegrityMetric(public val checked: Int, public val dangling: Int, public val examples: List<String>)

/**
 * What the graph says and how sure it is: relationships per evidence class and state, what is unresolved and why, and
 * the names that most often stay unresolved ([unresolvedByHint], most frequent first).
 */
@Serializable
public data class CoverageSummary(
    public val symbols: Int,
    public val relationships: Int,
    public val rows: List<CoverageRow>,
    public val unresolvedByReason: Map<String, Int>,
    public val unresolvedByHint: Map<String, Int>,
    public val diagnosticsBySeverity: Map<String, Int>,
    public val diagnosticsByCode: Map<String, Int>,
    public val integrity: IntegrityMetric,
)

/** Summarizes the coverage of a graph: the evidence and state of every relationship, and its integrity. */
internal class CoverageSummarizer(private val index: GraphIndex) {
    fun summarize(): CoverageSummary = CoverageSummary(
        symbols = index.symbols.size,
        relationships = index.relationships.size,
        rows = rows(),
        unresolvedByReason = unresolvedByReason(),
        unresolvedByHint = unresolvedByHint(),
        diagnosticsBySeverity = index.diagnostics.groupingBy { severityName(it.severity) }.eachCount().toSortedMap(),
        diagnosticsByCode = index.diagnostics.groupingBy { it.code.value }.eachCount().toSortedMap(),
        integrity = integrity(),
    )

    private fun rows(): List<CoverageRow> {
        val total = index.relationships.size.coerceAtLeast(1)
        return index.relationships.groupingBy { evidenceOf(it) to stateOf(it) }.eachCount()
            .map { (cell, count) -> CoverageRow(cell.first, cell.second, count, count.toDouble() / total) }
            .sortedWith(compareBy({ it.evidence }, { it.state }))
    }

    private fun stateOf(relationship: Relationship): String = when (val resolution = relationship.resolution) {
        is Resolution.Resolved -> resolution.certainty.name.lowercase()
        is Resolution.Unresolved -> UNRESOLVED
        is Resolution.NotAnalysed -> NOT_ANALYSED
    }

    private fun unresolvedByReason(): Map<String, Int> = index.relationships.mapNotNull { relationship ->
        when (val resolution = relationship.resolution) {
            is Resolution.Unresolved -> "$UNRESOLVED:${resolution.reason.value}"
            is Resolution.NotAnalysed -> "$NOT_ANALYSED:${resolution.reason.value}"
            is Resolution.Resolved -> null
        }
    }.groupingBy { it }.eachCount().toSortedMap()

    private fun unresolvedByHint(): Map<String, Int> = index.relationships
        .mapNotNull { (it.resolution as? Resolution.Unresolved)?.hint }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareBy({ -it.value }, { it.key }))
        .take(HINTS)
        .associate { it.key to it.value }

    private fun integrity(): IntegrityMetric {
        val targets = index.relationships.mapNotNull { index.resolvedTarget(it) }.filter(index::isWorkspaceTarget)
        val dangling = targets.filterNot(index::exists)
        val examples = dangling.map(NodeRef::key).distinct().sorted().take(EXAMPLES)
        return IntegrityMetric(targets.size, dangling.size, examples)
    }

    private fun severityName(severity: DiagnosticSeverity) = severity.name.lowercase()

    private companion object {
        const val UNRESOLVED = "unresolved"
        const val NOT_ANALYSED = "not-analysed"
        const val EXAMPLES = 10
        const val HINTS = 15
    }
}
