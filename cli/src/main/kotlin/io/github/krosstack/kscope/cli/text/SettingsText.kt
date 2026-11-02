package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.service.settings.AnalyzersReport
import io.github.krosstack.kscope.service.settings.LevelReport
import io.github.krosstack.kscope.service.settings.ScanReport
import io.github.krosstack.kscope.service.settings.SettingValue
import io.github.krosstack.kscope.service.settings.SettingsReport

/** The `Settings` section of `info`: the settings file, the scan, the analyzer switches and the resources. */
internal object SettingsText {
    private const val OUTDATED = "outdated"
    private val INDEX_STATES = mapOf(
        "current" to "index is current",
        OUTDATED to "index was built with other settings, scan again",
        "not-indexed" to "not indexed yet",
    )

    fun fields(report: SettingsReport): List<Pair<String, String>> {
        val problems = report.errors.mapIndexed { index, issue ->
            (if (index == 0) "Problems" else "") to issue.describe(report.file.orEmpty())
        }
        val resources = listOf(
            "Workers" to setting(report.resources.workers),
            "Worker heap" to setting(report.resources.workerHeapMib, " MiB"),
        )
        val head = listOf("File" to fileName(report)) + level(report.level) + scan(report.scan)
        return head + analyzers(report.analyzers) + resources + problems
    }

    private fun level(level: LevelReport) = listOf(
        "Level" to "${level.value} (${level.origin})",
        "Trust" to if (level.trusted) "trusted on this machine" else "not trusted on this machine",
    )

    private fun scan(scan: ScanReport): List<Pair<String, String>> {
        val narrowing = listOfNotNull(
            list("include", scan.include),
            list("exclude", scan.exclude),
            "tests off".takeIf { !scan.tests },
            list("generated", scan.generated),
        )
        if (narrowing.isEmpty() && scan.indexState != OUTDATED) return emptyList()
        val state = INDEX_STATES[scan.indexState]?.let { " ($it)" }.orEmpty()
        return listOf("Scan" to narrowing.ifEmpty { listOf("all files") }.joinToString("; ") + state)
    }

    private fun analyzers(analyzers: AnalyzersReport): List<Pair<String, String>> {
        val off = analyzers.disabled()
        if (off.isEmpty() && analyzers.indexState != OUTDATED) return emptyList()
        val state = INDEX_STATES[analyzers.indexState]?.let { " ($it)" }.orEmpty()
        val text = if (off.isEmpty()) "all on" else "off: ${off.joinToString(", ") { it.key }}"
        return listOf("Analyzers" to text + state)
    }

    private fun list(label: String, patterns: List<String>): String? =
        patterns.takeIf { it.isNotEmpty() }?.let { "$label ${it.joinToString(", ")}" }

    private fun fileName(report: SettingsReport): String {
        val file = report.file ?: return "none"
        return if (report.valid) file else "$file (not valid, so a scan will not start)"
    }

    private fun setting(setting: SettingValue, unit: String = ""): String {
        val value = setting.value?.let { "${Format.count(it)}$unit" } ?: "automatic"
        return "$value (${setting.origin})"
    }
}
