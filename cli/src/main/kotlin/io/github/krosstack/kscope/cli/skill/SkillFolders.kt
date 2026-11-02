package io.github.krosstack.kscope.cli.skill

import java.nio.file.Files
import java.nio.file.Path

/**
 * The personal skill folders of the coding agents in a home folder. Claude Code reads `~/.claude/skills`; Codex,
 * Gemini CLI, GitHub Copilot and other agents that follow the Agent Skills convention read `~/.agents/skills`. A
 * folder counts only when one of those agents has left its own folder in the home folder.
 */
internal class SkillFolders(private val home: Path) {
    fun all(): List<SkillTarget> = FOLDERS.map { SkillTarget(it.agents, home.resolve(it.skills)) }

    fun detected(): List<SkillTarget> = FOLDERS
        .filter { folder -> folder.signs.any { Files.isDirectory(home.resolve(it)) } }
        .map { SkillTarget(it.agents, home.resolve(it.skills)) }

    private class Folder(val agents: String, val skills: String, val signs: List<String>)

    companion object {
        const val CHOSEN = "the agents that read this folder"
        val SIGNS = listOf(".claude", ".agents", ".codex", ".gemini", ".copilot")

        private val FOLDERS = listOf(
            Folder("Claude Code", ".claude/skills", listOf(".claude")),
            Folder("Codex, Gemini CLI, GitHub Copilot and others", ".agents/skills", SIGNS - ".claude"),
        )

        fun chosen(folders: List<Path>): List<SkillTarget> = folders.map { SkillTarget(CHOSEN, it) }
    }
}
