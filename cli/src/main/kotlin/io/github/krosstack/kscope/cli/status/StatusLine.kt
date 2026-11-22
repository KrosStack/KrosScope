package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.text.FreshnessSummary
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.service.status.IndexStatus
import io.github.krosstack.kscope.service.status.ServerState
import io.github.krosstack.kscope.service.status.ServerStatus

internal object StatusLine {
    fun of(report: StatusReport): String = when (val index = report.index) {
        IndexStatus.NoIndex -> "kscope: no index in ${report.workspace}; run kscope scan"

        is IndexStatus.OtherFormat ->
            "kscope: the index in ${report.indexLocation} is of another format " +
                "(version ${index.found}; this kscope reads version ${index.expected}); run kscope scan to rebuild it"

        is IndexStatus.Damaged ->
            "kscope: the index in ${report.indexLocation} cannot be read (${index.reason}); " +
                "run kscope scan --rebuild"

        is IndexStatus.Present -> listOf(
            "index ${report.indexLocation}",
            "level ${index.level.wireName}",
            scanned(index),
            FreshnessSummary.of(index.freshness, index.repositories),
            server(report.server),
        ).joinToString("; ", prefix = "kscope: ")
    }

    private fun scanned(index: IndexStatus.Present): String =
        index.ageSeconds?.let { "scanned ${Format.age(it)} ago" } ?: "scan time not known"

    private fun server(server: ServerStatus): String = when (server.state) {
        ServerState.RUNNING -> "server running (pid ${server.pid})"
        ServerState.NOT_RUNNING -> "server not running"
        ServerState.UNKNOWN -> "server unknown (pid ${server.pid} is alive, but it cannot be checked)"
    }
}
