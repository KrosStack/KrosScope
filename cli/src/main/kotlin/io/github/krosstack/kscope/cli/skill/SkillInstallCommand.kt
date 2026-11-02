package io.github.krosstack.kscope.cli.skill

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.resolve
import java.nio.file.Path

internal class SkillInstallCommand(private val home: () -> Path, private val version: String) :
    CliktCommand(name = "install") {
    private val common by RootOptions(emptySet())
    private val folders by option(
        "--dir",
        metavar = "DIR",
        help = "A folder of skills to install into instead of the folders of the agents found, such as " +
            ".claude/skills of a project. Repeat it for several.",
    ).path(canBeFile = false).multiple()

    override fun help(context: Context) = "Installs the kscope skill for your coding agents.\n\n" +
        "It writes the skill to the personal skill folders of the coding agents it finds in your home folder: " +
        "~/.claude/skills for Claude Code, and ~/.agents/skills for Codex, Gemini CLI, GitHub Copilot and other " +
        "agents that read it. Run it again to update the skill to this version of kscope. A folder named kscope " +
        "that holds something else is left alone."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope skill install",
        "  kscope skill install --dir .claude/skills",
    ).joinToString("\n")

    override fun run() {
        resolve(common)
        val targets = if (folders.isEmpty()) SkillFolders(home()).detected() else SkillFolders.chosen(folders)
        if (targets.isEmpty()) {
            echo(
                "No coding agent found in ${home()} (looked for ${SkillFolders.SIGNS.joinToString(", ")}). " +
                    "Pass --dir to install the skill into a folder of your choice.",
            )
            return
        }
        val installer = SkillInstaller(SkillBundle(version).files())
        report(targets.map { it to installer.install(it.directory) })
    }
}
