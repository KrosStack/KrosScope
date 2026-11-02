package io.github.krosstack.kscope.cli.skill

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Path

internal class SkillCommand(
    home: () -> Path = { Path.of(System.getProperty("user.home")) },
    version: String = KrosScope.toolVersion(),
) : CliktCommand(name = "skill") {
    private val common by RootOptions(emptySet())

    init {
        subcommands(SkillInstallCommand(home, version), SkillUninstallCommand(home), SkillShowCommand(version))
    }

    override val helpTags = CommandGroup.tag(CommandGroup.SERVE)

    override val printHelpOnEmptyArgs = true

    override fun help(context: Context): String = listOf(
        "Installs the skill that teaches coding agents to use kscope.",
        "A skill is a folder of instructions that a coding agent such as Claude Code, Codex, Gemini CLI or " +
            "GitHub Copilot loads when a request calls for it. With the kscope skill installed, an agent knows " +
            "which kscope commands to run, how to read their results and which ones it must not run on its own.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope skill install",
        "  kscope skill show",
        "  kscope skill uninstall",
    ).joinToString("\n")

    override fun run() {
        resolve(common)
    }
}
