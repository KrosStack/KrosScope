package io.github.krosstack.kscope.cli.service.page

import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.text.format.Format

internal object StatusPageHtml {
    const val TITLE = "KrosScope service"
    const val NO_WORKSPACE = "The service keeps no workspace."
    private const val BYTES_PER_MIB = 1_048_576L

    fun render(model: StatusPageModel): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\">")
        appendLine("<head>")
        appendLine("<meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<meta name=\"referrer\" content=\"no-referrer\">")
        appendLine("<title>$TITLE</title>")
        appendLine("<style>$STYLE</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<h1>$TITLE</h1>")
        appendLine("<p>This page only shows. Settings are changed in kscope.toml and service.toml.</p>")
        append(service(model))
        append(workspaces(model.workspaces))
        appendLine("</body>")
        appendLine("</html>")
    }

    private fun service(model: StatusPageModel): String = buildString {
        appendLine("<h2>Service</h2>")
        append(
            DefinitionList.html(
                listOf(
                    "Version" to model.version,
                    "Process" to model.pid.toString(),
                    "Started" to model.startedAt,
                    "Limits" to limits(model.limits),
                    "Memory" to memory(model),
                ),
            ),
        )
    }

    private fun limits(limits: ServiceLimits): String = limits.text.removePrefix("${ServiceLimits.LINE_PREFIX} ")
        .trimEnd('.')

    private fun memory(model: StatusPageModel): String =
        "heap in use ${mebibytes(model.heapUsedBytes)} of ${mebibytes(model.heapMaximumBytes)}"

    private fun mebibytes(bytes: Long): String = "${Format.count(bytes / BYTES_PER_MIB)} MiB"

    private fun workspaces(workspaces: List<WorkspaceStatus>): String = buildString {
        appendLine("<h2>Workspaces</h2>")
        if (workspaces.isEmpty()) {
            appendLine("<p>$NO_WORKSPACE</p>")
        } else {
            workspaces.forEach { append(WorkspaceSectionHtml.html(it)) }
        }
    }

    private const val STYLE = "body{font-family:system-ui,sans-serif;margin:2rem;max-width:60rem;" +
        "color:#1b1b1b;background:#fff}dl{display:grid;grid-template-columns:max-content 1fr;gap:.25rem 1rem}" +
        "dt{font-weight:600}dd{margin:0;overflow-wrap:anywhere}section{border-top:1px solid #ccc;" +
        "margin-top:1rem}.errors{color:#9b1c1c}"
}
