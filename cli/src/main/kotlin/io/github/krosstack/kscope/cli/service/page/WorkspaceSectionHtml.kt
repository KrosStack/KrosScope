package io.github.krosstack.kscope.cli.service.page

import io.github.krosstack.kscope.cli.serve.ScanRecord
import io.github.krosstack.kscope.cli.text.format.Format

internal object WorkspaceSectionHtml {
    fun html(status: WorkspaceStatus): String = buildString {
        appendLine("<section>")
        appendLine("<h3>${HtmlEscape.text(status.root)}</h3>")
        append(DefinitionList.html(rows(status)))
        if (status.errors.isNotEmpty()) append(errors(status.errors))
        appendLine("</section>")
    }

    private fun rows(status: WorkspaceStatus): List<Pair<String, String>> = listOfNotNull(
        "State" to status.state,
        status.indexed?.let { "Index" to if (it) "built" else "none; run kscope scan" },
        status.indexLevel?.let { "Index level" to it },
        status.inMemory?.let { "Graph" to if (it) "in memory" else "released from memory" },
        "Last scan" to lastScan(status.lastScan),
        status.watcher?.let { "Watcher" to it.removePrefix("Watcher: ") },
        "Settings file" to status.settingsFile,
        "Workers" to status.workers,
        "Worker heap" to status.workerHeap,
    )

    private fun lastScan(record: ScanRecord?): String = when {
        record == null -> "none since the service took the workspace up"
        record.failure != null -> "failed at ${record.finishedAt} after ${duration(record)}"
        else -> "${record.finishedAt}, took ${duration(record)}"
    }

    private fun duration(record: ScanRecord): String = "${Format.count(record.durationMillis)} ms"

    private fun errors(errors: List<String>): String = buildString {
        appendLine("<h4>Errors</h4>")
        appendLine("<ul class=\"errors\">")
        errors.forEach { appendLine("<li>${HtmlEscape.text(it)}</li>") }
        appendLine("</ul>")
    }
}
