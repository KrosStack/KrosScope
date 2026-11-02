package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.trust.TrustStore

internal class TrustCommand(private val store: () -> TrustStore = { TrustStore.ofThisMachine() }) :
    CliktCommand(name = "trust") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)
    private val revoke by option(
        "--revoke",
        help = "Stop trusting the workspace.",
    ).flag()

    override val helpTags = CommandGroup.tag(CommandGroup.INDEX)

    override fun help(context: Context) = "Trusts the workspace to run its own build on this machine.\n\n" +
        "Scans at level exact, and scans that refresh an exact index, run the project's own Gradle build, which " +
        "executes the project's code: trust only workspaces whose code you trust. The permission is kept in " +
        "your user settings (~/.kscope/service), never in the project, so a kscope.toml in the project can " +
        "ask for level exact but cannot grant it. `kscope info` shows whether the workspace is trusted."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope trust",
        "  kscope trust --revoke --workspace path/to/app",
    ).joinToString("\n")

    override fun run() {
        val workspace = resolve(common).workspace
        val changed = guarded { if (revoke) store().revoke(workspace) else store().grant(workspace) }
        echo(message(workspace.toString(), changed))
    }

    private fun message(workspace: String, changed: Boolean): String = when {
        revoke && changed -> "No longer trusting $workspace. Scans there will not run its build unless asked to."
        revoke -> "$workspace was not trusted."
        changed -> "Trusting $workspace. Its build may run in scans at level exact."
        else -> "$workspace is trusted already."
    }
}
