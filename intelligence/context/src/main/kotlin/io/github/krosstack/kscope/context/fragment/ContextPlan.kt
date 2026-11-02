package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRole

internal data class ContextPlan(val candidates: List<ContextCandidate>, val omitted: List<ContextOmission>)

internal class ContextPlanner(private val graph: ContextGraph) {
    fun ranked(input: ContextInput, level: ContextLevel): ContextPlan {
        val ranked = ranking(input)
        val bodies = ranked.filterNot { it.role == ContextRole.TEST && level < ContextLevel.DEEP }
        val selected = mass(bodies, level)
        val included = selected.map { it.node }.toSet()
        val omitted = ranked.filter { it.node !in included }
            .map { ContextOmission(it.node, ContextOmissionReason.LEVEL_LIMIT) }
        return ContextPlan(selected, omitted)
    }

    fun plan(input: ContextInput, level: ContextLevel): ContextPlan {
        val testFiles = input.tests.mapNotNull(graph::artifact).toSet()
        val ranked = ranking(input)
        val selected = mass(ranked, level)
        val sources = sourceNodes(selected)
        val relevance = ranked.maxOfOrNull { it.relevance } ?: 1.0
        val boundary = input.boundaries.filter { it.from in sources || it.target() in sources }
            .flatMap { listOfNotNull(it.from, it.target()) }
            .map { ContextCandidate(it, relevance, ContextRole.BOUNDARY) }
        val selectedTests = tests(input, level, ranked, relevance)
        val deep = if (level == ContextLevel.DEEP) {
            graph.neighbors(sources).map { ContextCandidate(it, relevance) }
        } else {
            emptyList()
        }
        val candidates = unique((selected + boundary + selectedTests + deep).map { testRole(it, testFiles) })
        val included = candidates.map { it.node }.toSet()
        val omitted = ranked.filter { it.node !in included }
            .map { ContextOmission(it.node, ContextOmissionReason.LEVEL_LIMIT) }
        return ContextPlan(candidates, omitted)
    }

    private fun ranking(input: ContextInput): List<ContextCandidate> {
        val testFiles = input.tests.mapNotNull(graph::artifact).toSet()
        return unique(input.candidates.map { testRole(it, testFiles) })
            .filter { it.relevance > 0 && it.relevance.isFinite() }
    }

    private fun tests(
        input: ContextInput,
        level: ContextLevel,
        candidates: List<ContextCandidate>,
        defaultRelevance: Double,
    ): List<ContextCandidate> = when (level) {
        ContextLevel.MINIMAL -> emptyList()

        ContextLevel.RECOMMENDED, ContextLevel.DEEP, ContextLevel.FULL -> input.tests.map { node ->
            ContextCandidate(node, candidates.find { it.node == node }?.relevance ?: defaultRelevance, ContextRole.TEST)
        }
    }

    private fun testRole(candidate: ContextCandidate, files: Set<ArtifactRef>): ContextCandidate =
        if (candidate.role != ContextRole.EDIT && graph.artifact(candidate.node) in files) {
            candidate.copy(role = ContextRole.TEST)
        } else {
            candidate
        }

    private fun sourceNodes(candidates: List<ContextCandidate>): Set<NodeRef> = candidates
        .mapNotNull { graph.artifact(it.node) }.distinct().flatMap(graph::nodes).toSet()

    private fun mass(candidates: List<ContextCandidate>, level: ContextLevel): List<ContextCandidate> {
        val fraction = when (level) {
            ContextLevel.MINIMAL -> MINIMAL_MASS
            ContextLevel.RECOMMENDED, ContextLevel.DEEP -> RECOMMENDED_MASS
            ContextLevel.FULL -> 1.0
        }
        val target = candidates.sumOf { it.relevance } * fraction
        var covered = 0.0
        return candidates.takeWhile { candidate ->
            (covered < target).also { covered += candidate.relevance }
        }
    }

    private fun unique(candidates: List<ContextCandidate>): List<ContextCandidate> = candidates
        .groupBy { it.node }.map { (_, matches) ->
            matches.maxWith(compareBy<ContextCandidate> { it.relevance }.thenBy { ROLE_PRIORITY.indexOf(it.role) })
        }.sortedWith(compareByDescending<ContextCandidate> { it.relevance }.thenBy { contextNodeKey(it.node) })

    private companion object {
        const val MINIMAL_MASS = 0.5
        const val RECOMMENDED_MASS = 0.8
        val ROLE_PRIORITY = listOf(ContextRole.READ, ContextRole.TEST, ContextRole.BOUNDARY, ContextRole.EDIT)
    }
}
