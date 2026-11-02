package io.github.krosstack.kscope.cli.skill

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Writes the skill into a skill directory and removes it again. It replaces or removes only a directory that holds
 * the kscope skill or nothing, never a link, so a skill of the same name from elsewhere is left alone.
 */
internal class SkillInstaller(private val files: List<SkillFile>) {
    fun install(directory: Path): SkillOutcome = attempt(directory) {
        val existed = Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
        if (existed && !holdsKscopeSkill(directory)) return@attempt SkillOutcome.Foreign(directory)
        replace(directory)
        if (existed) SkillOutcome.Updated(directory) else SkillOutcome.Installed(directory)
    }

    fun uninstall(directory: Path): SkillOutcome = attempt(directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return@attempt SkillOutcome.Absent(directory)
        if (!holdsKscopeSkill(directory)) return@attempt SkillOutcome.Foreign(directory)
        deleteTree(directory)
        SkillOutcome.Removed(directory)
    }

    private fun attempt(directory: Path, change: () -> SkillOutcome): SkillOutcome = try {
        change()
    } catch (failure: IOException) {
        SkillOutcome.Failed(directory, failure.message ?: failure.javaClass.simpleName)
    }

    private fun replace(directory: Path) {
        val parent = Files.createDirectories(directory.toAbsolutePath().parent)
        val staging = Files.createTempDirectory(parent, ".kscope-skill-")
        try {
            files.forEach { file ->
                val target = staging.resolve(file.path)
                Files.createDirectories(target.parent)
                Files.writeString(target, file.text)
            }
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) deleteTree(directory)
            Files.move(staging, directory)
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) deleteTree(staging)
        }
    }

    private fun holdsKscopeSkill(directory: Path): Boolean {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) return false
        val skill = directory.resolve(SkillBundle.SKILL_FILE)
        return if (Files.isRegularFile(skill, LinkOption.NOFOLLOW_LINKS)) namesKscope(skill) else isEmpty(directory)
    }

    private fun namesKscope(skill: Path): Boolean = Files.newBufferedReader(skill).useLines { lines ->
        lines.take(FRONT_MATTER_LINES).any { it.trim() == NAME_LINE }
    }

    private fun isEmpty(directory: Path): Boolean = Files.list(directory).use { it.findAny().isEmpty }

    private fun deleteTree(directory: Path) {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private companion object {
        const val FRONT_MATTER_LINES = 5
        const val NAME_LINE = "name: ${SkillBundle.NAME}"
    }
}
