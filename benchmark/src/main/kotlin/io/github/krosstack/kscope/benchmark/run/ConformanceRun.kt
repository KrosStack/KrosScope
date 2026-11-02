package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.gold.GoldLoader
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Scans the reference workspace into a throwaway index below [indexParent], so the workspace stays untouched, and
 * scores the result against the gold data in [goldDirectory].
 */
public class ConformanceRun(
    private val workspace: Path,
    private val goldDirectory: Path,
    private val indexParent: Path,
) {
    public fun run(mode: ScanMode): ConformanceResult {
        val gold = GoldLoader.load(goldDirectory)
        val index = Files.createTempDirectory(indexParent, "kscope-conformance-")
        try {
            val options = measuredOpenOptions(index, trustBuild = mode == ScanMode.TRUSTED, level = mode.level)
            return KrosScope.open(workspace, options).use { scope ->
                val started = System.nanoTime()
                val scan = scope.scan(rebuild = true)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                ConformanceScoring(scope.graph(), gold).score(mode, metricsOf(scan, elapsed))
            }
        } finally {
            index.toFile().deleteRecursively()
        }
    }

    private fun metricsOf(scan: ScanResult, totalMillis: Long) = ScanMetrics(
        artifacts = scan.artifacts,
        unitsProduced = scan.unitsProduced,
        stageMillis = scan.stageMillis.toSortedMap(),
        totalMillis = totalMillis,
        errors = scan.diagnostics.errors,
        warnings = scan.diagnostics.warnings,
        infos = scan.diagnostics.infos,
    )
}
