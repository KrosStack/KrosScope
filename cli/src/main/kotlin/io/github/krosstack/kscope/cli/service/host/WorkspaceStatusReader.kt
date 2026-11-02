package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.service.page.WorkspaceStatus
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.settings.SettingValue
import io.github.krosstack.kscope.service.settings.SettingsReport
import java.nio.file.Path

internal object WorkspaceStatusReader {
    fun read(path: Path, state: WorkspaceState): WorkspaceStatus {
        val server = (state as? WorkspaceState.Served)?.server
        val settings = runCatching { KrosScope.settingsReport(path) }.getOrNull()
        val lastScan = server?.lastScan
        return WorkspaceStatus(
            root = path.toString(),
            state = state.text,
            indexed = server?.indexed,
            indexLevel = server?.indexLevel,
            inMemory = server?.loaded,
            lastScan = lastScan,
            watcher = server?.watchState?.status(),
            settingsFile = settings?.let(::fileText) ?: UNREADABLE,
            workers = settings?.let { setting(it.resources.workers) } ?: UNREADABLE,
            workerHeap = settings?.let { setting(it.resources.workerHeapMib, " MiB") } ?: UNREADABLE,
            errors = listOfNotNull((state as? WorkspaceState.NotServed)?.reason) +
                settings?.errors.orEmpty().map { it.describe(settings?.file.orEmpty()) } +
                listOfNotNull(lastScan?.failure?.let { "The last scan failed: $it" }),
        )
    }

    private fun fileText(report: SettingsReport): String {
        val file = report.file ?: return "none"
        return if (report.valid) file else "$file (not valid, so a scan will not start)"
    }

    private fun setting(setting: SettingValue, unit: String = ""): String {
        val value = setting.value?.let { "${Format.count(it)}$unit" } ?: "automatic"
        return "$value (${setting.origin})"
    }

    private const val UNREADABLE = "unreadable"
}
