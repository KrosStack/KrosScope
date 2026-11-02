package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.Score

/** Renders the metrics of a conformance run as Markdown tables, for the console and for `report.md`. */
public class MarkdownReport(private val result: ConformanceResult) {
    private val details = MarkdownDetails(result)

    public fun render(): String = listOf(
        "# Conformance on the reference workspace: ${result.mode} mode",
        scanSection(),
        gateSection(),
        buildModelSection(),
        declarationSection(),
        closedWorldSection(),
        details.kinds(),
        details.classes(),
        details.coverage(),
        details.differences(),
    ).joinToString("\n\n") + "\n"

    private fun scanSection(): String {
        val scan = result.scan
        val stages = scan.stageMillis.entries.joinToString(", ") { "${it.key} ${it.value} ms" }
        return table(
            title = "Scan",
            header = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Wall time", "${scan.totalMillis} ms"),
                listOf("Stages", stages),
                listOf("Artifacts", scan.artifacts.toString()),
                listOf("Units produced", scan.unitsProduced.toString()),
                listOf("Symbols", result.coverage.symbols.toString()),
                listOf("Relationships", result.coverage.relationships.toString()),
                listOf("Diagnostics", "${scan.errors} errors, ${scan.warnings} warnings, ${scan.infos} infos"),
            ),
        )
    }

    private fun gateSection(): String {
        val selection = result.gateSelection()
        val outcome = result.checkGate(selection)
        val verdict = if (outcome.passed) "met" else "NOT met: " + outcome.failures.joinToString("; ")
        val edges = result.closedWorld.overall(selection)
        val deferred = result.closedWorld.deferredCount(selection)
        val withGenerated = Score.of(
            matched = edges.matched,
            missed = edges.expected - edges.matched + deferred,
            extra = edges.produced - edges.matched,
        )
        val rows = listOf(
            scoreRow("Declarations (all files)", result.declarations.overall),
            scoreRow("Closed-world edges ($selection)", edges),
            scoreRow("Same, generated targets counted as missed", withGenerated),
        )
        return "## Gate ($selection)\n\nThresholds are $verdict.\n\n" +
            table(null, listOf("Metric", "Precision", "Recall", "F1", "Expected", "Matched"), rows) +
            "\n\n$deferred gold edges of the selected files point to generated surfaces that no producer makes yet; " +
            "the gate does not score them."
    }

    private fun buildModelSection(): String {
        val model = result.buildModel
        val dimensions = model.dimensions.map { it.name to it.score } + ("overall" to model.overall)
        return table(
            title = "Build model against gold",
            header = listOf("Dimension", "Precision", "Recall", "Expected", "Missing", "Extra"),
            rows = dimensions.map { (name, score) -> countsRow(name, score) },
        )
    }

    private fun declarationSection(): String = table(
        title = "Declarations per file",
        header = listOf("File", "Precision", "Recall", "Expected", "Missing", "Extra"),
        rows = result.declarations.files.map { countsRow(it.file.substringAfterLast('/'), it.score) },
    )

    private fun closedWorldSection(): String = table(
        title = "Closed-world edges per file",
        header = listOf("File", "Android", "Precision", "Recall", "Expected", "Matched", "Extra", "Generated"),
        rows = result.closedWorld.files.map { file ->
            listOf(
                file.file.substringAfterLast('/'),
                if (file.androidDependent) "yes" else "no",
                format(file.score.precision),
                format(file.score.recall),
                file.score.expected.toString(),
                file.score.matched.toString(),
                file.score.extras.size.toString(),
                file.deferred.size.toString(),
            )
        },
    )
}
