package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship

/** Scores the links of a graph against the gold relationships of one class. */
internal class WorkspaceLinkScorer(private val graph: WorkspaceGraph) {
    fun score(name: String, linkClass: LinkClass?, gold: List<GoldRelationship>): LinkClassResult {
        val produced = linkClass?.producer?.produce(graph).orEmpty().distinct()
        val matches = gold.associateWith { entry -> linkClass?.let { linksOf(it, entry, produced) }.orEmpty() }
        val found = matches.filterValues { it.isNotEmpty() }
        val withEvidence = found.filter { (entry, links) -> links.any { it.evidence == entry.expectedEvidence } }
        return LinkClassResult(
            name = name,
            scan = phaseOf(gold).label,
            goldCount = gold.size,
            found = found.size,
            recall = if (gold.isEmpty()) 1.0 else found.size.toDouble() / gold.size,
            withExpectedEvidence = withEvidence.size,
            unmatched = (produced.toSet() - matches.values.flatten().toSet()).size,
            missing = gold.filter { it !in found }.map { "${it.id}: ${it.from.describe()} -> ${it.to.describe()}" },
            evidenceMismatches = found.filterKeys { it !in withEvidence }.map { (entry, links) ->
                val produced = links.map { it.evidence }.distinct().sorted()
                "${entry.id}: expected ${entry.expectedEvidence}, produced $produced"
            },
        )
    }

    private fun linksOf(
        linkClass: LinkClass,
        entry: GoldRelationship,
        produced: List<ProducedLink>,
    ): List<ProducedLink> {
        val from = linkClass.end(entry.from)
        val to = linkClass.end(entry.to)
        return produced.filter { from.accepts(it.from) && to.accepts(it.to) }
    }

    companion object {
        fun phaseOf(gold: List<GoldRelationship>): ScanPhase =
            if (gold.any { it.requiresOutput }) ScanPhase.BUILD_OUTPUTS else ScanPhase.SOURCES
    }
}
