package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.model.AnswerKey
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.answerKey
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.graph.GraphReader

/**
 * Checks that a linker answered each symbolic reference of the namespaces it claims with exactly one relationship, so
 * that no reference is silently left without a resolution state. Findings are error diagnostics stored with the
 * linker's output.
 */
internal class SymbolicReferenceAudit(private val linker: BoundaryLinker) {
    fun findings(graph: GraphReader, emitted: List<Fact>): List<Fact> {
        val claimed = linker.namespaces.sorted().flatMap { graph.symbolicReferences(it) }
        if (claimed.isEmpty()) return emptyList()
        val expected = claimed.groupingBy { it.answerKey() }.eachCount()
        val actual = emitted.filterIsInstance<Fact.OfRelationship>().map { it.value.answerKey() }
            .filter { it in expected }.groupingBy { it }.eachCount()
        val byKey = claimed.groupBy { it.answerKey() }
        val unlinked = expected.filter { (key, count) -> (actual[key] ?: 0) < count }.keys
        val duplicated = expected.filter { (key, count) -> (actual[key] ?: 0) > count }.keys
        return listOfNotNull(
            report(UNLINKED, "no relationship", unlinked, byKey),
            report(DUPLICATED, "more than one relationship", duplicated, byKey),
        ).map { Fact.OfDiagnostic(it) }
    }

    private fun report(
        code: String,
        problem: String,
        keys: Set<AnswerKey>,
        byKey: Map<AnswerKey, List<SymbolicReference>>,
    ): Diagnostic? {
        val references = keys.flatMap { byKey.getValue(it) }.sortedWith(compareBy({ it.namespace }, { it.key }))
        val example = references.firstOrNull() ?: return null
        val place = example.site?.let { " at ${it.artifact.path.value}:${it.range.startLine}" }.orEmpty()
        return Diagnostic(
            severity = DiagnosticSeverity.ERROR,
            code = ReasonCode(code),
            message = "Linker ${linker.descriptor.producer.id} gave $problem to ${references.size} symbolic " +
                "references, for example ${example.namespace} ${example.key}$place.",
            location = example.site,
        )
    }

    private companion object {
        const val UNLINKED = "engine.symbolic-reference-unlinked"
        const val DUPLICATED = "engine.symbolic-reference-duplicated"
    }
}
