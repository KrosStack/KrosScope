package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.ClassPart
import io.github.krosstack.kscope.benchmark.score.ClassScore

/** The sections of the report that explain the headline numbers: edge kinds, classes, coverage and differences. */
internal class MarkdownDetails(private val result: ConformanceResult) {
    fun kinds(): String {
        val byKind = result.closedWorld.files.flatMap { it.byKind }.groupBy { it.kind }
        return table(
            title = "Closed-world recall by edge kind",
            header = listOf("Kind", "Expected", "Matched"),
            rows = byKind.toSortedMap().map { (kind, counts) ->
                listOf(kind, counts.sumOf { it.expected }.toString(), counts.sumOf { it.matched }.toString())
            },
        )
    }

    fun classes(): String = table(
        title = "Relationship classes",
        header = listOf("Class", "Gold", "Precision", "Recall", "Missing", "Extra", "Evidence differs", "Accepted"),
        rows = result.relationshipClasses.flatMap { entry -> listOf(classRow(entry)) + entry.parts.map(::partRow) },
    )

    private fun classRow(entry: ClassScore): List<String> {
        val score = entry.score
            ?: return listOf(entry.name, entry.goldCount.toString(), "-", "-", "-", "-", NOT_YET, "-")
        val counts = listOf(score.misses.size.toString(), score.extras.size.toString())
        val ratios = listOf(format(score.precision), format(score.recall))
        val evidence = listOf(entry.evidenceMismatches.toString(), accepted(entry.acceptedEvidence))
        return listOf(entry.name, entry.goldCount.toString()) + ratios + counts + evidence
    }

    private fun partRow(part: ClassPart): List<String> {
        val score = part.score
        return listOf("- ${part.label}", score.expected.toString(), format(score.precision), format(score.recall)) +
            listOf(score.misses.size.toString(), score.extras.size.toString(), part.evidenceMismatches.toString()) +
            accepted(part.acceptedEvidence)
    }

    private fun accepted(counts: Map<String, Int>): String =
        if (counts.isEmpty()) "-" else counts.entries.joinToString(", ") { "${it.value} as ${it.key}" }

    fun coverage(): String {
        val coverage = result.coverage
        val evidence = table(
            title = "Relationships by evidence class and resolution state",
            header = listOf("Evidence", "State", "Count", "Share"),
            rows = coverage.rows.map { listOf(it.evidence, it.state, it.count.toString(), format(it.share)) },
        )
        val total = coverage.relationships.coerceAtLeast(1)
        val reasons = table(
            title = "Unresolved by reason",
            header = listOf("Reason", "Count", "Share"),
            rows = coverage.unresolvedByReason.map { (reason, count) ->
                listOf(reason, count.toString(), format(count.toDouble() / total))
            },
        )
        val hints = table(
            title = "Most frequent unresolved names",
            header = listOf("Name", "Count"),
            rows = coverage.unresolvedByHint.map { listOf(it.key, it.value.toString()) },
        )
        val integrity = coverage.integrity
        val summary = "Integrity: ${integrity.dangling} of ${integrity.checked} resolved workspace targets name " +
            "nothing the graph has."
        return listOf(evidence, reasons, hints, summary).joinToString("\n\n")
    }

    private fun classListing(entry: ClassScore): String? = entry.score?.let { listing("class: ${entry.name}", it) }

    fun differences(): String {
        val sections = result.buildModel.dimensions.map { listing("build model: ${it.name}", it.score) } +
            result.declarations.files.map { listing("declarations: ${it.file}", it.score) } +
            result.closedWorld.files.map { listing("edges: ${it.file}", it.score, it.unmapped) } +
            result.relationshipClasses.mapNotNull { classListing(it) } +
            listing("gold problems", result.goldProblems.map { "- $it" })
        return (listOf("## Differences") + sections.filter { it.isNotEmpty() }).joinToString("\n\n")
    }

    private companion object {
        const val NOT_YET = "not yet supported"
    }
}
