package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/** Scans the reference workspace once per mode, into a throwaway index, and keeps the report for inspection. */
internal object ConformanceFixture {
    private val workspace: Path = Path.of(checkNotNull(System.getProperty("kscope.fixture")))
    private val gold: Path = Path.of(checkNotNull(System.getProperty("kscope.gold")))
    private val reports: Path = Path.of("build", "conformance")
    private val prettyJson = Json(from = KscopeJson) { prettyPrint = true }

    val syntax: ConformanceResult by lazy { measure(ScanMode.SYNTAX) }

    val default: ConformanceResult by lazy { measure(ScanMode.DEFAULT) }

    val trusted: ConformanceResult by lazy { measure(ScanMode.TRUSTED) }

    private fun measure(mode: ScanMode): ConformanceResult {
        val scratch = Files.createTempDirectory("kscope-conformance-test")
        try {
            val result = ConformanceRun(workspace, gold, scratch).run(mode)
            publish(mode, result)
            return result
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    private fun publish(mode: ScanMode, result: ConformanceResult) {
        val directory = Files.createDirectories(reports.resolve(mode.label))
        val report = MarkdownReport(result).render()
        Files.writeString(directory.resolve("report.md"), report)
        Files.writeString(directory.resolve("metrics.json"), prettyJson.encodeToString(result) + "\n")
        println(report)
    }
}
