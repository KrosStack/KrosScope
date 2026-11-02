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

internal class SkillUninstallCommand(private val home: () -> Path) : CliktCommand(name = "uninstall") {
    private val common by RootOptions(emptySet())
    private val folders by option(
        "--dir",
        metavar = "DIR",
        help = "A folder of skills to remove the skill from instead of ~/.claude/skills and ~/.agents/skills. " +
            "Repeat it for several.",
    ).path(canBeFile = false).multiple()

    override fun help(context: Context) = "Removes the kscope skill from your coding agents.\n\n" +
        "It removes the kscope folder from ~/.claude/skills and ~/.agents/skills, or from the folders given with " +
        "--dir, only where it holds the kscope skill."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope skill uninstall",
        "  kscope skill uninstall --dir .claude/skills",
    ).joinToString("\n")

    override fun run() {
        resolve(common)
        val targets = if (folders.isEmpty()) SkillFolders(home()).all() else SkillFolders.chosen(folders)
        val installer = SkillInstaller(emptyList())
        report(targets.map { it to installer.uninstall(it.directory) })
    }
}
