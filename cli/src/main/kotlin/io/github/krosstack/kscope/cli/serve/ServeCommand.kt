package io.github.krosstack.kscope.cli.serve

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import java.util.concurrent.TimeUnit

internal class ServeCommand : CliktCommand(name = "serve") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)
    private val stop by option("--stop", help = "Stop the server that runs for this workspace.").flag()
    private val watch by option(
        "--watch",
        help = "Keep the index up to date: notice changed files and scan them between requests. A scan it " +
            "starts runs the project's build code only in a workspace you trusted with `kscope trust`.",
    ).flag()
    private val map by option(
        "--map",
        help = "Also serve a live map of the workspace to a browser, on this machine only, at an address with a " +
            "random token. It reads the index and changes nothing.",
    ).flag()
    private val status by option(
        "--status",
        help = "Say whether a server runs for this workspace, with the address of its map if it serves one. The " +
            "exit code is 0 when one does and 1 when none does.",
    ).flag()
    private val idleTimeout by option(
        "--idle-timeout",
        metavar = "MINUTES",
        help = "Stop by itself after this many minutes without a request. 0 never stops.",
    )
        .long().restrictTo(0, MAX_IDLE_MINUTES).default(0)

    override fun help(context: Context): String = listOf(
        "Keeps the index in memory so that other commands answer faster.",
        "Use it for one workspace in this terminal. It runs in the foreground until Ctrl+C or `kscope serve " +
            "--stop`, and meanwhile commands such as analyze, context, impact and scan for the workspace are " +
            "answered by it and print the same as without it. To keep several workspaces ready in the " +
            "background, use `kscope service`.",
        "It listens only on this machine and answers only requests that carry the token it wrote to " +
            ".kscope/serve.json when it started.",
    ).joinToString("\n\n")

    override val helpTags = CommandGroup.tag(CommandGroup.SERVE)

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope serve",
        "  kscope serve --idle-timeout 5 --workspace path/to/app",
        "  kscope serve --watch --map",
        "  kscope serve --status",
        "  kscope serve --stop",
    ).joinToString("\n")

    override fun run() {
        validateFlags()
        val options = resolve(common)
        when {
            stop -> stop(options)
            status -> status(options)
            else -> start(options)
        }
    }

    private fun validateFlags() {
        if (stop && status) throw UsageError("--stop and --status cannot be combined.")
        val flag = foregroundFlag()
        if (flag != null && (stop || status)) throw UsageError("$flag requires foreground serving.")
    }

    private fun foregroundFlag(): String? = when {
        watch -> "--watch"
        map -> "--map"
        idleTimeout != 0L -> "--idle-timeout"
        else -> null
    }

    private fun start(options: ResolvedOptions) = guarded {
        val settings = ServeSettings(TimeUnit.MINUTES.toMillis(idleTimeout), watchEnabled = watch, mapEnabled = map)
        ServeServer.open(options.workspace, settings).use { server ->
            val hook = Thread({
                server.requestStop()
                server.awaitTermination()
            }, "kscope-serve-shutdown")
            Runtime.getRuntime().addShutdownHook(hook)
            try {
                echo(
                    "Serving ${options.workspace}.${if (watch) " Watching the workspace." else ""} " +
                        "Stop with Ctrl+C or `kscope serve --stop` for this workspace.",
                    err = true,
                )
                server.mapUrl?.let { echo("Map: $it", err = true) }
                server.run()
            } finally {
                removeHook(hook)
            }
        }
    }

    private fun stop(options: ResolvedOptions) {
        val metadata = ServeMetadata.read(options.workspace)
        var explained = false
        val code = metadata?.let {
            val request = ServeRequest(token = it.token, action = "stop")
            ServeClient.exchange(it, request, requireCompatible = false) { chunk ->
                explained = true
                echo(chunk.text, trailingNewline = false, err = chunk.stream == "stderr")
            }
        }
        // A server that stops closes its connections, and may cut this one before its exit code arrives.
        when {
            code == null -> echo("No server runs for this workspace.")
            code != 0 && explained -> throw ProgramResult(code)
        }
    }

    private fun status(options: ResolvedOptions) {
        val metadata = ServeMetadata.read(options.workspace)
        if (metadata == null || !ServeClient.alive(metadata)) {
            echo("No server runs for this workspace.")
            throw ProgramResult(ExitCodes.FAILURE)
        }
        echo(
            "Server runs for ${options.workspace}: pid ${metadata.pid}, version ${metadata.toolVersion}, " +
                "started ${metadata.startTime}.",
        )
        val answered = ServeClient.exchange(
            metadata,
            ServeRequest(token = metadata.token, action = "status"),
            requireCompatible = false,
            answerTimeoutMillis = STATUS_TIMEOUT_MILLIS,
        ) { echo(it.text, trailingNewline = false, err = it.stream == "stderr") }
        if (answered == null) echo("The server did not answer within 2 seconds; it may be busy with a request.")
    }

    private fun removeHook(hook: Thread) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (_: IllegalStateException) {
            return
        }
    }

    private companion object {
        const val MAX_IDLE_MINUTES = Long.MAX_VALUE / 60_000
        const val STATUS_TIMEOUT_MILLIS = 2_000
    }
}
