package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.SystemEntries
import io.github.krosstack.kscope.cli.service.launch.BackgroundStarter
import io.github.krosstack.kscope.cli.service.launch.JvmProcess
import io.github.krosstack.kscope.cli.service.launch.ServiceStarter

internal class ServiceCommand(
    entries: EntryFactory = SystemEntries.ofThisMachine(),
    starter: ServiceStarter = BackgroundStarter(),
    jvm: () -> JvmProcess = { JvmProcess.current() },
) : CliktCommand(name = "service") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)

    init {
        subcommands(
            ServiceAddCommand(),
            ServiceRemoveCommand(),
            ServiceStartCommand(starter, jvm),
            ServiceStatusCommand(entries),
            ServicePageCommand(),
            ServiceStopCommand(),
            ServiceInstallCommand(entries, jvm),
            ServiceUninstallCommand(entries),
            ServiceRunCommand(),
        )
    }

    override val helpTags = CommandGroup.tag(CommandGroup.SERVE)

    override val printHelpOnEmptyArgs = true

    override fun help(context: Context): String = listOf(
        "Keeps several workspaces ready in the background.",
        "Where `serve` handles one workspace in this terminal, the service is one background process of yours " +
            "that serves every workspace you `add`, so that `kscope mcp` and the other commands answer from " +
            "memory. `start` runs it in the background, and `install` makes it start with your session.",
        "It listens only on this machine and answers only requests that carry its token. Like `serve --watch` " +
            "it keeps indexes up to date, and runs a workspace's build code only if you trusted that workspace " +
            "with `kscope trust` and it is, or asks to be, indexed at level exact.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service add --workspace path/to/app",
        "  kscope service start",
        "  kscope service status",
        "  kscope service stop",
        "  kscope service install",
    ).joinToString("\n")

    override fun run() {
        currentContext.obj = resolve(common)
    }
}
