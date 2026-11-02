package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind

internal class TestSelection(private val graph: RetrievalGraph) {
    fun select(editFiles: List<RankedFile>): List<RankedFile> {
        val edits = editFiles.filter { it.role == FileRole.EDIT }.map { it.artifact }.toSet()
        val subjects = graph.nodes(edits.filterNot(::isRunnableTest).toSet())
        val related = graph.edges.mapNotNull { candidate(it, subjects) }
        val edited = edits.filter(::isRunnableTest).map {
            RankedFile(it, EDIT_SCORE, FileRole.EDIT, listOf(SignalContribution(SignalKind.TEST_EDIT, EDIT_SCORE)))
        }
        val ranked = (edited + related).groupBy { it.artifact }.map { (file, matches) -> combine(file, matches) }
            .sortedWith(
                compareByDescending<RankedFile> { it.score }.thenBy { graph.locations.pathOf(it.artifact) }
                    .thenBy { it.artifact.repository.value },
            )
        return cut(ranked)
    }

    private fun candidate(edge: Relationship, subjects: Set<NodeRef>): RankedFile? {
        val resolution = edge.resolution as? Resolution.Resolved
        val file = graph.file(edge.from)?.takeIf(::isRunnableTest)
        val signal = signal(edge)
        if (resolution == null || file == null || signal == null) return null
        val targets = if (signal == SignalKind.TEST_RELATION) subjects.flatMap(::ancestors).toSet() else subjects
        val score = (if (signal == SignalKind.TEST_RELATION) SUBJECT_SCORE else REFERENCE_SCORE) *
            certaintyWeight(resolution.certainty)
        val matches = ancestors(resolution.target).any { matchesSubject(it, targets) }
        return if (matches) {
            RankedFile(file, score, FileRole.READ, listOf(SignalContribution(signal, score)))
        } else {
            null
        }
    }

    private fun matchesSubject(node: NodeRef, subjects: Set<NodeRef>): Boolean {
        val source: NodeRef? = graph.file(node)?.let(NodeRef::OfArtifact)
        return node in subjects || (source != null && source in subjects)
    }

    private fun signal(edge: Relationship): SignalKind? = when {
        edge.kind == RelationKind.TESTS && edge.attributes["tests.subject-basis"] in SUBJECT_BASES ->
            SignalKind.TEST_RELATION

        edge.kind in REFERENCES && edge.from is NodeRef.OfSymbol -> SignalKind.TEST_REFERENCE

        else -> null
    }

    private fun ancestors(node: NodeRef): Set<NodeRef> = buildSet {
        var current: NodeRef? = node
        while (current != null && add(current)) {
            val symbol = current as? NodeRef.OfSymbol
            current = symbol?.let { graph.symbols[it.id]?.container }?.let(NodeRef::OfSymbol)
        }
    }

    private fun isRunnableTest(file: ArtifactRef): Boolean = graph.artifacts[file]?.sourceSet
        ?.let { graph.sourceSets[it]?.role } in RUNNABLE_ROLES

    private fun combine(file: ArtifactRef, matches: List<RankedFile>): RankedFile {
        val reasons = matches.flatMap { it.reasons }.groupBy { it.signal }
            .map { (signal, values) -> SignalContribution(signal, values.maxOf { it.score }) }.sortedBy { it.signal }
        val role = if (matches.any { it.role == FileRole.EDIT }) FileRole.EDIT else FileRole.READ
        return RankedFile(file, matches.maxOf { it.score }, role, reasons)
    }

    private fun cut(ranked: List<RankedFile>): List<RankedFile> {
        val capped = ranked.take(MAX_TESTS)
        val gap = capped.zipWithNext().indexOfFirst { (left, right) ->
            right.role != FileRole.EDIT && left.score >= right.score * CLEAR_GAP
        }
        return if (gap < 0) capped else capped.take(gap + 1)
    }

    private fun certaintyWeight(certainty: Certainty): Double = when (certainty) {
        Certainty.EXACT -> 1.0
        Certainty.PROBABLE -> PROBABLE_WEIGHT
        Certainty.HEURISTIC -> HEURISTIC_WEIGHT
    }

    private companion object {
        const val MAX_TESTS = 20
        const val SUBJECT_SCORE = 4.0
        const val REFERENCE_SCORE = 1.0
        const val EDIT_SCORE = 4.0
        const val CLEAR_GAP = 2.0
        const val PROBABLE_WEIGHT = 0.7
        const val HEURISTIC_WEIGHT = 0.4
        val RUNNABLE_ROLES = setOf(SourceSetRole.UNIT_TEST, SourceSetRole.INSTRUMENTED_TEST)
        val REFERENCES = setOf(RelationKind.REFERENCES, RelationKind.CALLS, RelationKind.TYPE_USES)
        val SUBJECT_BASES = setOf(null, "name", "references")
    }
}
