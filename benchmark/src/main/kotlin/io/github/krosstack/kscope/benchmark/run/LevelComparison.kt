package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.ClassScore

/**
 * Sets the conformance results of the scan levels side by side: the scan time, the headline scores of the files that
 * each gate covers, and the precision and recall of every relationship class. The timings are the only part that
 * varies between runs.
 */
public class LevelComparison(results: List<ConformanceResult>) {
    private val results = results.sortedBy { order(it.level) }

    public fun data(): LevelComparisonData = LevelComparisonData(
        levels = results.map(::summary),
        classes = classNames().map(::across),
    )

    public fun render(): String {
        val data = data()
        return listOf(
            "# Conformance per scan level",
            summaryTable(data),
            classTable(data),
        ).joinToString("\n\n") + "\n"
    }

    private fun summary(result: ConformanceResult): LevelSummary {
        val selection = result.gateSelection()
        val edges = result.closedWorld.overall(selection)
        val outcome = result.checkGate(selection)
        return LevelSummary(
            level = result.level,
            totalMillis = result.scan.totalMillis,
            stageMillis = result.scan.stageMillis,
            declarationPrecision = result.declarations.overall.precision,
            declarationRecall = result.declarations.overall.recall,
            edgePrecision = edges.precision,
            edgeRecall = edges.recall,
            relationships = result.coverage.relationships,
            compilerRelationships = result.coverage.rows.filter { it.evidence == "compiler" }.sumOf { it.count },
            gateMet = outcome.passed,
            gateFailures = outcome.failures,
        )
    }

    private fun classNames(): List<String> = results.flatMap { it.relationshipClasses }.map { it.name }.distinct()

    private fun across(name: String): ClassAcrossLevels {
        val entries = results.map { it to it.relationshipClasses.firstOrNull { entry -> entry.name == name } }
        return ClassAcrossLevels(
            name = name,
            goldCount = entries.firstNotNullOfOrNull { it.second?.goldCount } ?: 0,
            levels = entries.map { (result, entry) -> atLevel(result.level, entry) },
        )
    }

    private fun atLevel(level: String, entry: ClassScore?): ClassAtLevel {
        val score = entry?.score
        return ClassAtLevel(
            level = level,
            precision = score?.precision,
            recall = score?.recall,
            missing = score?.misses?.size ?: 0,
            extra = score?.extras?.size ?: 0,
            evidenceDiffers = entry?.evidenceMismatches ?: 0,
        )
    }

    private fun summaryTable(data: LevelComparisonData): String = table(
        title = "Scan and gate",
        header = SUMMARY_HEADER,
        rows = data.levels.map {
            listOf(
                it.level,
                "${it.totalMillis} ms",
                "${format(it.declarationPrecision)} / ${format(it.declarationRecall)}",
                "${format(it.edgePrecision)} / ${format(it.edgeRecall)}",
                it.relationships.toString(),
                it.compilerRelationships.toString(),
                if (it.gateMet) "met" else "NOT met: " + it.gateFailures.joinToString("; "),
            )
        },
    )

    private fun classTable(data: LevelComparisonData): String = table(
        title = "Relationship classes: precision / recall per level",
        header = listOf("Class", "Gold") + data.levels.map { it.level },
        rows = data.classes.map { entry ->
            listOf(entry.name, entry.goldCount.toString()) + entry.levels.map(::cell)
        },
    )

    private fun cell(entry: ClassAtLevel): String {
        val precision = entry.precision
        val recall = entry.recall
        return if (precision == null || recall == null) NOT_SCORED else "${format(precision)} / ${format(recall)}"
    }

    private companion object {
        const val NOT_SCORED = "-"
        val SUMMARY_HEADER = listOf(
            "Level",
            "Wall time",
            "Declarations P / R",
            "Closed-world edges P / R",
            "Relationships",
            "With compiler evidence",
            "Gate",
        )
        val LEVEL_ORDER = ScanMode.entries.map { it.level.wireName }

        fun order(level: String): Int = LEVEL_ORDER.indexOf(level)
    }
}
