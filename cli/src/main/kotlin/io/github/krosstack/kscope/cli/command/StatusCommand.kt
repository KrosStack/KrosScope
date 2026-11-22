package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.ClientPaths
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.lookup.IndexSource
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.requireNoDiagramFormat
import io.github.krosstack.kscope.cli.resolveReading
import io.github.krosstack.kscope.cli.status.ServerCheck
import io.github.krosstack.kscope.cli.status.StatusLine
import io.github.krosstack.kscope.cli.status.StatusReport
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.status.IndexStatus
import io.github.krosstack.kscope.service.status.status

internal class StatusCommand(private val server: ServerCheck = ServerCheck()) : CliktCommand(name = "status") {
    private val common by RootOptions(setOf(SharedOption.WORKSPACE, SharedOption.FORMAT), FormatGuide.TEXT_OR_JSON)

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Says in one line whether the index can be trusted.\n\n" +
        "Shows which index answers, its level, how long ago it was scanned, how far behind it is (every " +
        "repository, its commit and its working tree) and whether a server runs. It reads the index file and " +
        "the Git files and changes nothing. It never starts a server and never asks one. Exit code 0 when an " +
        "index can be read, whether or not it is behind; 1 when there is none or it cannot be read."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope status\n  kscope status --format json"

    override fun run() {
        val options = resolveReading(common, forStatus = true)
        requireNoDiagramFormat(options.format)
        val report = StatusReport(
            workspace = options.workspace.toString().replace('\\', '/'),
            indexLocation = ClientPaths.indexLocation(options.workspace),
            index = guarded { indexOf(options) },
            server = server.of(options.workspace),
        )
        echoResult(render(report, options.format), options.format)
        if (!report.readable) {
            if (options.format == OutputFormat.JSON) echo(StatusLine.of(report), err = true)
            throw ProgramResult(ExitCodes.FAILURE)
        }
    }

    private fun indexOf(options: ResolvedOptions): IndexStatus = if (options.source == IndexSource.Missing) {
        IndexStatus.NoIndex
    } else {
        KrosScope.open(options.workspace, OpenOptions(tokenCache = false)).use { it.status() }
    }

    private fun render(report: StatusReport, format: OutputFormat): String = when (format) {
        OutputFormat.JSON -> ServiceJson.encode(report.result())
        else -> StatusLine.of(report)
    }
}
