package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.gold.GoldSymbolMatcher
import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal object LocalizationAnalysisMetrics {
    private const val LIMIT = 10

    fun score(
        result: AnalysisResult,
        task: LocalizationTask,
        structure: WorkspaceStructure,
        names: WorkspaceNames = WorkspaceNames.SINGLE,
    ): LocalizationAnalysisScores {
        val symbols = result.symbols.take(LIMIT).map { it.symbol }
        val addresses = task.symbols.distinct()
        val matcher = GoldSymbolMatcher(structure, names)
        val matched = addresses.count { address -> symbols.any { matcher.matches(it, address) } }
        val tests = result.tests.map { names.of(it.artifact) }.toSet()
        val testScores = task.relevantTests?.let { gold ->
            LocalizationSetMetrics.score(tests, gold.map { it.file }.toSet())
        }
        val modules = structure.modules.filter { it.id in result.scope.modules }.map { it.path }.toSet()
        val sets = structure.sourceSets.filter { it.id in result.scope.sourceSets }.map { it.name }.toSet()
        val scope = task.changeScope
        val moduleScores = scope?.let { LocalizationSetMetrics.score(modules, it.modules.toSet()) }
        val setScores = scope?.let { LocalizationSetMetrics.score(sets, it.sourceSets.toSet()) }
        val variantScores = scope?.let {
            LocalizationSetMetrics.score(result.scope.variants.toSet(), it.variants.toSet())
        }
        val exact = listOfNotNull(moduleScores, setScores, variantScores).all { it.exactMatch == 1.0 }
        return LocalizationAnalysisScores(
            symbolRecall10 = if (addresses.isEmpty()) null else matched.toDouble() / addresses.size,
            testPrecision = testScores?.precision,
            testRecall = testScores?.recall,
            modulePrecision = moduleScores?.precision,
            moduleRecall = moduleScores?.recall,
            moduleExactMatch = moduleScores?.exactMatch,
            sourceSetPrecision = setScores?.precision,
            sourceSetRecall = setScores?.recall,
            sourceSetExactMatch = setScores?.exactMatch,
            variantPrecision = variantScores?.precision,
            variantRecall = variantScores?.recall,
            variantExactMatch = variantScores?.exactMatch,
            scopeAccuracy = scope?.let { if (exact) 1.0 else 0.0 },
        )
    }

    fun mean(scores: List<LocalizationAnalysisScores>): LocalizationAnalysisScores? = if (scores.isEmpty()) {
        null
    } else {
        LocalizationAnalysisScores(
            symbolRecall10 = average(scores.mapNotNull { it.symbolRecall10 }),
            testPrecision = average(scores.mapNotNull { it.testPrecision }),
            testRecall = average(scores.mapNotNull { it.testRecall }),
            modulePrecision = average(scores.mapNotNull { it.modulePrecision }),
            moduleRecall = average(scores.mapNotNull { it.moduleRecall }),
            moduleExactMatch = average(scores.mapNotNull { it.moduleExactMatch }),
            sourceSetPrecision = average(scores.mapNotNull { it.sourceSetPrecision }),
            sourceSetRecall = average(scores.mapNotNull { it.sourceSetRecall }),
            sourceSetExactMatch = average(scores.mapNotNull { it.sourceSetExactMatch }),
            variantPrecision = average(scores.mapNotNull { it.variantPrecision }),
            variantRecall = average(scores.mapNotNull { it.variantRecall }),
            variantExactMatch = average(scores.mapNotNull { it.variantExactMatch }),
            scopeAccuracy = average(scores.mapNotNull { it.scopeAccuracy }),
        )
    }

    private fun average(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()
}
