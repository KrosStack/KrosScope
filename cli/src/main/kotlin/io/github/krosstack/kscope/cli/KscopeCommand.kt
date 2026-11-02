package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parsers.CommandLineParser
import io.github.krosstack.kscope.cli.command.AnalyzeCommand
import io.github.krosstack.kscope.cli.command.BenchmarkCommand
import io.github.krosstack.kscope.cli.command.ContextCommand
import io.github.krosstack.kscope.cli.command.DoctorCommand
import io.github.krosstack.kscope.cli.command.GraphCommand
import io.github.krosstack.kscope.cli.command.ImpactCommand
import io.github.krosstack.kscope.cli.command.InfoCommand
import io.github.krosstack.kscope.cli.command.RelatedCommand
import io.github.krosstack.kscope.cli.command.ScanCommand
import io.github.krosstack.kscope.cli.command.SymbolCommand
import io.github.krosstack.kscope.cli.command.TrustCommand
import io.github.krosstack.kscope.cli.mcp.McpCommand
import io.github.krosstack.kscope.cli.serve.ServeCommand
import io.github.krosstack.kscope.cli.service.ServiceCommand
import io.github.krosstack.kscope.cli.skill.SkillCommand
import io.github.krosstack.kscope.cli.workspace.WorkspaceCommand
import io.github.krosstack.kscope.service.KrosScope

internal class KscopeCommand(
    private val environment: CliEnvironment = CliEnvironment.detached(),
    private val execution: CommandExecution = CommandExecution(),
    serviceCommand: () -> ServiceCommand = { ServiceCommand() },
) : CliktCommand(name = "kscope") {
    private val defaults by RootOptions()

    init {
        subcommands(
            ScanCommand(),
            ScanCommand("update", compatibilityAlias = true),
            ContextCommand(),
            AnalyzeCommand(),
            InfoCommand(),
            SymbolCommand(),
            ImpactCommand(),
            RelatedCommand(),
            GraphCommand(),
        )
        if (!execution.serving) {
            subcommands(TrustCommand(), WorkspaceCommand(), DoctorCommand(), BenchmarkCommand())
            subcommands(ServeCommand(), serviceCommand(), McpCommand(), SkillCommand())
        }
        versionOption(KrosScope.toolVersion())
        configureContext {
            helpFormatter = { KscopeHelpFormatter(it) }
            environment.terminal?.let { terminal = it }
            readArgumentFile = if (execution.serving) null else execution::readArgumentFile
        }
    }

    override val printHelpOnEmptyArgs = true

    override fun help(context: Context) =
        "Indexes a Kotlin, Android or Kotlin Multiplatform project (the workspace) and answers which files a " +
            "task touches, what a change affects and what to give a coding agent. The index is a searchable " +
            "record of the code, kept in a .kscope folder. Everything runs on this machine. Run `kscope scan` " +
            "first."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope scan",
        "  kscope analyze \"add an optional reminder to tasks\"",
        "  kscope context \"add an optional reminder to tasks\" --budget 8000",
        "  kscope impact path/to/File.kt",
        "",
        "Results go to standard output; progress and errors go to standard error.",
        "Exit codes: 0 success, 1 failure, 2 usage error or ambiguous name,",
        "3 verification mismatch. `kscope <command> --help` shows one command.",
        "Full reference: https://github.com/KrosStack/KrosScope/blob/main/docs/cli.md",
    ).joinToString("\n")

    override fun run() {
        currentContext.obj = CliSession(defaults, environment, execution)
    }

    fun parseResolved(argv: List<String>) {
        val parsed = CommandLineParser.parse(this, execution.prepare(argv))
        val resolved = execution.prepare(parsed.expandedArgv)
        val invocation = if (resolved == parsed.expandedArgv) {
            parsed.invocation
        } else {
            CommandLineParser.parse(this, resolved).invocation
        }
        CommandLineParser.run(invocation) { it.run() }
    }
}
