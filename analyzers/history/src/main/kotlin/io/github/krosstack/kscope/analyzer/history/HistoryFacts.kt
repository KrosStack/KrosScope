package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.HistorySignals
import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope

/** The facts for a [HistorySummary] of the files of [artifacts], in the order of their paths. */
internal class HistoryFacts(artifacts: List<Artifact>) {
    private val nodes: Map<String, NodeRef> = artifacts.associate { it.ref.path.value to NodeRef.OfArtifact(it.ref) }

    fun of(summary: HistorySummary): List<Fact> {
        val metrics = summary.files.flatMap { (path, history) -> metricsOf(path, history) }
        return metrics + summary.coChanges.map { Fact.OfRelationship(relationshipOf(it)) }
    }

    private fun metricsOf(path: String, history: FileHistory): List<Fact> {
        val node = nodes.getValue(path)
        return listOf(
            Fact.OfMetric(Metric(node, HistorySignals.COMMITS, history.commits.toDouble())),
            Fact.OfMetric(Metric(node, HistorySignals.AUTHORS, history.authors.toDouble())),
            Fact.OfMetric(Metric(node, HistorySignals.RECENCY_RANK, history.recencyRank.toDouble())),
        )
    }

    private fun relationshipOf(coChange: CoChange) = Relationship(
        from = nodes.getValue(coChange.from),
        kind = RelationKind.CO_CHANGES_WITH,
        subKind = null,
        resolution = Resolution.Resolved(nodes.getValue(coChange.to), Certainty.HEURISTIC),
        evidenceClass = EvidenceClass.HISTORICAL,
        site = null,
        scope = Scope(),
        attributes = mapOf(
            HistorySignals.SUPPORT to coChange.support.toString(),
            HistorySignals.CONFIDENCE to coChange.confidence,
        ),
    )
}
