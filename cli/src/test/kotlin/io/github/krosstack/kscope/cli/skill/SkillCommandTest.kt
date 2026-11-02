package io.github.krosstack.kscope.cli.skill

import com.github.ajalt.clikt.testing.test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillCommandTest {
    private fun <T> withHome(vararg folders: String, block: (Path) -> T): T {
        val home = Files.createTempDirectory("kscope-skill-command")
        try {
            folders.forEach { Files.createDirectories(home.resolve(it)) }
            return block(home)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    private fun skill(home: Path) = SkillCommand(home = { home }, version = "9.8.7")

    @Test
    fun `install writes the skill for every agent found and says where`() = withHome(".claude", ".codex") { home ->
        val result = skill(home).test(listOf("install"))

        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stdout.contains("for Claude Code in ${home.resolve(".claude/skills/kscope")}"), result.stdout)
        assertTrue(result.stdout.contains("in ${home.resolve(".agents/skills/kscope")}"), result.stdout)
        listOf(".claude/skills/kscope", ".agents/skills/kscope").forEach {
            val text = Files.readString(home.resolve(it).resolve("SKILL.md"))
            assertTrue(text.startsWith("---\nname: kscope\n") && text.contains("kscope 9.8.7"), it)
            assertTrue(Files.exists(home.resolve(it).resolve("references/commands.md")), it)
        }
    }

    @Test
    fun `install again updates the skill`() = withHome(".claude") { home ->
        skill(home).test(listOf("install"))

        val again = skill(home).test(listOf("install"))

        assertEquals(0, again.statusCode)
        assertTrue(again.stdout.startsWith("Updated the kscope skill for Claude Code"), again.stdout)
    }

    @Test
    fun `install without a coding agent writes nothing and says how to choose a folder`() = withHome { home ->
        val result = skill(home).test(listOf("install"))

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("No coding agent found") && result.stdout.contains("--dir"), result.stdout)
        assertEquals(0L, Files.list(home).use { it.count() })
    }

    @Test
    fun `install into a chosen folder ignores the agents of the home folder`() = withHome(".claude") { home ->
        val project = home.resolve("project/.claude/skills")

        val result = skill(home).test(listOf("install", "--dir", project.toString()))

        assertEquals(0, result.statusCode, result.output)
        assertTrue(Files.exists(project.resolve("kscope/SKILL.md")))
        assertFalse(Files.exists(home.resolve(".claude/skills")))
    }

    @Test
    fun `a folder that holds another skill makes install fail and stays as it was`() = withHome(".claude") { home ->
        val foreign = Files.createDirectories(home.resolve(".claude/skills/kscope"))
        Files.writeString(foreign.resolve("SKILL.md"), "---\nname: someone-else\n---\n")

        val result = skill(home).test(listOf("install"))

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("Left $foreign alone"), result.stderr)
        assertTrue(Files.readString(foreign.resolve("SKILL.md")).contains("someone-else"))
    }

    @Test
    fun `uninstall removes the skill wherever install put it`() = withHome(".claude", ".gemini") { home ->
        skill(home).test(listOf("install"))

        val result = skill(home).test(listOf("uninstall"))

        assertEquals(0, result.statusCode, result.output)
        assertFalse(Files.exists(home.resolve(".claude/skills/kscope")))
        assertFalse(Files.exists(home.resolve(".agents/skills/kscope")))
        assertEquals(2, result.stdout.lines().count { it.startsWith("Removed the kscope skill") }, result.stdout)
    }

    @Test
    fun `show prints the skill with the version of the tool`() = withHome { home ->
        val result = skill(home).test(listOf("show"))

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.startsWith("---\nname: kscope\n"), result.stdout.take(80))
        assertTrue(result.stdout.contains("kscope 9.8.7"), result.stdout.take(800))
    }
}
