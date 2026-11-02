package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.terminal
import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeRefusals
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeServices
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.indexLevel
import java.nio.file.Path

internal class CommandExecution(
    val directory: Path = Path.of("").toAbsolutePath(),
    private val service: ServeServices? = null,
    private val serverWorkspace: Path? = null,
    private val scanRunner: ScanRunner = ScanRunner.DEFAULT,
) {
    val serving: Boolean get() = service != null
    var arguments: List<String> = emptyList()
        private set

    fun prepare(argv: List<String>): List<String> = ClientPaths.resolve(argv, directory).also { arguments = it }

    fun readArgumentFile(file: String): String = ClientPaths.readArgumentFile(file, directory)

    fun refuseStructure(command: CliktCommand) {
        if (serving) {
            command.echo("This command is not served.", err = true)
            throw ProgramResult(ExitCodes.USAGE)
        }
    }

    fun forward(command: CliktCommand, options: ResolvedOptions) {
        if (service == null) forwardLocally(command, options) else requireWorkspace(options)
    }

    private fun forwardLocally(command: CliktCommand, options: ResolvedOptions) {
        val scan = command.commandName == "scan" || command.commandName == "update"
        if (options.noServer && !scan) return
        ServeMetadata.read(options.workspace)?.let { metadata ->
            routeServer(metadata, command, options, scan)
        }
    }

    private fun routeServer(metadata: ServeMetadata, command: CliktCommand, options: ResolvedOptions, scan: Boolean) {
        if (options.noServer || !ServeClient.compatible(metadata)) {
            if (ServeClient.alive(metadata)) {
                if (options.noServer) {
                    ServeRefusals.optedOutScan(command)
                } else {
                    ServeRefusals.versionMismatch(command, scan)
                }
            }
        } else {
            send(metadata, command, options, scan)
        }
    }

    private fun send(metadata: ServeMetadata, command: CliktCommand, options: ResolvedOptions, scan: Boolean) {
        val request = ServeRequest(
            metadata.token,
            ClientPaths.resolve(arguments, directory, unescapeAt = true),
            directory.toString(),
            ClientRendering.of(command, options.environment),
        )
        val code = ServeClient.exchange(metadata, request) { chunk ->
            command.terminal.rawPrint(chunk.text, stderr = chunk.stream == "stderr")
        } ?: run {
            if (scan && ServeClient.alive(metadata)) ServeRefusals.unavailableScan(command)
            return
        }
        if (code == ServeClient.VERSION_MISMATCH) {
            ServeRefusals.versionMismatch(command, scan)
        } else {
            throw ProgramResult(code)
        }
    }

    fun <T> useService(options: ResolvedOptions, opening: OpenOptions, work: (KrosScope) -> T): T {
        requireWorkspace(options)
        return if (service == null) KrosScope.open(options.workspace, opening).use(work) else service.use(work)
    }

    fun scan(options: ResolvedOptions, opening: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult {
        requireWorkspace(options)
        return service?.scan(opening, rebuild, verify) ?: scanRunner.run(options.workspace, opening, rebuild, verify)
    }

    private fun requireWorkspace(options: ResolvedOptions) {
        if (serverWorkspace != null && options.workspace.toRealPath() != serverWorkspace) {
            throw UsageError("A server request must use the server's workspace.")
        }
    }
}

internal fun CliktCommand.forward(options: ResolvedOptions) {
    val session = currentContext.parent?.obj as? CliSession
    session?.execution?.forward(this, options)
}

internal fun <T> CliktCommand.useService(
    options: ResolvedOptions,
    opening: OpenOptions = OpenOptions(),
    work: (KrosScope) -> T,
): T {
    val session = currentContext.parent?.obj as? CliSession
    return (session?.execution ?: CommandExecution()).useService(options, opening) { scope ->
        work(scope).also { noteSyntaxLevel(scope, options) }
    }
}

private fun CliktCommand.noteSyntaxLevel(scope: KrosScope, options: ResolvedOptions) {
    val answersByItself = commandName == "info"
    if (!answersByItself && options.format != OutputFormat.JSON && scope.indexLevel() == ScanLevel.SYNTAX) {
        echo(SYNTAX_NOTE, err = true)
    }
}

private const val SYNTAX_NOTE = "Index level: syntax; references are not analysed."

internal fun CliktCommand.scanService(
    options: ResolvedOptions,
    opening: OpenOptions,
    rebuild: Boolean,
    verify: Boolean,
): ScanResult {
    val session = currentContext.parent?.obj as? CliSession
    return (session?.execution ?: CommandExecution()).scan(options, opening, rebuild, verify)
}
