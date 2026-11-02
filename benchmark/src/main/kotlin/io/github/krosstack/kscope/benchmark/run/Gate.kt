package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.Score

/** The thresholds of the Kotlin analyzer gate: declarations and closed-world edges of the files a gate covers. */
public object GateThresholds {
    public const val DECLARATION_PRECISION: Double = 0.99
    public const val DECLARATION_RECALL: Double = 0.99
    public const val EDGE_PRECISION: Double = 0.98
    public const val EDGE_RECALL: Double = 0.95
}

/** The outcome of checking a result against the gate. [failures] says what is below its threshold. */
public class GateOutcome(public val failures: List<String>) {
    public val passed: Boolean get() = failures.isEmpty()
}

/** The files a gate covers: all of them when the build was trusted, otherwise those without the Android classpath. */
public fun ConformanceResult.gateSelection(): FileSelection =
    if (mode == ScanMode.TRUSTED.label) FileSelection.ALL else FileSelection.WITHOUT_ANDROID

internal const val NOT_ANALYSED_AT_SYNTAX = "kotlin.references.not-analysed-at-syntax"

/**
 * Checks declaration precision and recall, edge precision and recall of [selection], the integrity of the graph and
 * the agreement of the build model with gold. The scan must hold no error, the gold data must hold no problem, and
 * declarations and edges must have been measured: a ratio over nothing is 1 and proves nothing. At level `syntax` the
 * recall of edges is reported and not gated, because nothing is resolved there; instead the scan must hold no edge
 * with compiler evidence and must say that references were not analysed.
 */
public fun ConformanceResult.checkGate(selection: FileSelection): GateOutcome {
    val edges = closedWorld.overall(selection)
    val declarations = declarations.overall
    val atSyntax = level == ScanMode.SYNTAX.level.wireName
    val checks = listOf(
        check("declaration precision", declarations.precision, GateThresholds.DECLARATION_PRECISION),
        check("declaration recall", declarations.recall, GateThresholds.DECLARATION_RECALL),
        check("edge precision", edges.precision, GateThresholds.EDGE_PRECISION),
        check("edge recall", edges.recall, GateThresholds.EDGE_RECALL).takeIf { !atSyntax },
        "integrity: ${coverage.integrity.dangling} dangling targets".takeIf { coverage.integrity.dangling != 0 },
        "build model differs from gold in ${buildModel.overall.misses.size + buildModel.overall.extras.size} places"
            .takeIf { !buildModel.overall.isExact },
    ) + measurementChecks(declarations, edges) + if (atSyntax) syntaxChecks() else emptyList()
    return GateOutcome(checks.filterNotNull())
}

private fun ConformanceResult.measurementChecks(declarations: Score, edges: Score): List<String?> = listOf(
    "no declaration was measured".takeIf { declarations.expected == 0 },
    "no closed-world edge was measured".takeIf { edges.expected == 0 },
    "the scan reported ${scan.errors} errors".takeIf { scan.errors != 0 },
    "the gold data holds ${goldProblems.size} problems".takeIf { goldProblems.isNotEmpty() },
)

private fun ConformanceResult.syntaxChecks(): List<String?> {
    val compilerEdges = coverage.rows.filter { it.evidence == COMPILER }.sumOf { it.count }
    return listOf(
        "$compilerEdges relationships have compiler evidence at level syntax".takeIf { compilerEdges != 0 },
        "no source set says that references were not analysed"
            .takeIf { (coverage.diagnosticsByCode[NOT_ANALYSED_AT_SYNTAX] ?: 0) == 0 },
    )
}

private const val COMPILER = "compiler"

private fun check(name: String, value: Double, threshold: Double): String? =
    "$name ${format(value)} is below ${format(threshold)}".takeIf { value < threshold }

internal fun format(value: Double): String = "%.4f".format(java.util.Locale.ROOT, value)
