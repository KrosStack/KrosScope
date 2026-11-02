package io.github.krosstack.kscope.cli.mcp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.serve.ServeSettings

internal class McpCommand : CliktCommand(name = "mcp") {
    private val common by RootOptions(setOf(SharedOption.WORKSPACE, SharedOption.NO_SERVER))
    private val watch by option(
        "--watch",
        help = "Keep the index up to date while the agent works. A scan it starts runs the project's build code " +
            "only in a workspace you trusted with `kscope trust`.",
    ).flag()

    override val helpTags = CommandGroup.tag(CommandGroup.SERVE)

    override fun help(context: Context): String =
        "Lets a coding agent use kscope as a tool (Model Context Protocol).\n\n" +
            "An agent that supports the protocol starts this command and talks to it on standard input and " +
            "output; you do not type into it. It keeps the workspace ready with a server of its own when none " +
            "runs, until standard input closes. With --no-server each tool reads the index directly."

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope mcp",
        "  kscope mcp --workspace path/to/app --watch",
        "  kscope --no-server mcp",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common)
        val streams = McpStreams(
            System.`in`,
            System.out.writer(Charsets.UTF_8).buffered(),
            System.err.writer(Charsets.UTF_8).buffered(),
        )
        McpSession(options.workspace, streams, McpSettings(options.noServer, ServeSettings(watchEnabled = watch))).run()
    }
}
