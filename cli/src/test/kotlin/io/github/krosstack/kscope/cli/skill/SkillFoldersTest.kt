package io.github.krosstack.kscope.cli.skill

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillFoldersTest {
    private fun <T> withHome(vararg folders: String, block: (Path) -> T): T {
        val home = Files.createTempDirectory("kscope-skill-home")
        try {
            folders.forEach { Files.createDirectories(home.resolve(it)) }
            return block(home)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a home without coding agents has no skill folder`() = withHome { home ->
        assertEquals(emptyList(), SkillFolders(home).detected())
    }

    @Test
    fun `claude code is found by its own folder`() = withHome(".claude") { home ->
        assertEquals(listOf(home.resolve(".claude/skills")), SkillFolders(home).detected().map { it.skills })
    }

    @Test
    fun `the shared folder is chosen for codex gemini and copilot`() {
        listOf(".codex", ".gemini", ".copilot", ".agents").forEach { agent ->
            withHome(agent) { home ->
                assertEquals(listOf(home.resolve(".agents/skills")), SkillFolders(home).detected().map { it.skills })
            }
        }
    }

    @Test
    fun `every agent folder found gets the skill once`() = withHome(".claude", ".codex", ".copilot") { home ->
        val folders = SkillFolders(home).detected().map { it.skills }

        assertEquals(listOf(home.resolve(".claude/skills"), home.resolve(".agents/skills")), folders)
        assertEquals(folders, SkillFolders(home).all().map { it.skills })
    }

    @Test
    fun `the skill takes a folder named kscope inside a skill folder`() {
        val target = SkillFolders.chosen(listOf(Path.of("project", ".claude", "skills"))).single()

        assertEquals(Path.of("project", ".claude", "skills", "kscope"), target.directory)
    }
}
