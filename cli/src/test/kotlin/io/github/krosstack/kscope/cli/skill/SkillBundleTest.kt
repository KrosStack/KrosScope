package io.github.krosstack.kscope.cli.skill

import com.github.ajalt.clikt.core.CoreCliktCommand
import io.github.krosstack.kscope.cli.KscopeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkillBundleTest {
    private val files = SkillBundle("9.8.7").files()

    @Test
    fun `the bundle holds the skill and its command reference`() {
        assertEquals(SkillBundle.PATHS, files.map { it.path })
        files.forEach { assertTrue(it.text.isNotBlank(), "${it.path} is empty") }
    }

    @Test
    fun `the skill opens with the front matter that agents read`() {
        val lines = SkillBundle("9.8.7").skill().lines()
        assertEquals("---", lines[0])
        assertEquals("name: kscope", lines[1])
        val description = lines[2].removePrefix("description: ")
        assertTrue(lines[2].startsWith("description: "), lines[2])
        assertTrue(description.length <= MAX_DESCRIPTION, "the description has ${description.length} characters")
        assertTrue(description.contains("kscope") && description.contains("KrosScope"), description)
        assertEquals("---", lines[3])
    }

    @Test
    fun `the version of the tool is written into the skill`() {
        val skill = SkillBundle("9.8.7").skill()
        assertTrue(skill.contains("kscope 9.8.7"), skill.take(600))
        files.forEach { assertFalse(it.text.contains("{{"), "${it.path} keeps a placeholder") }
    }

    @Test
    fun `every command that the skill names exists`() {
        val root = KscopeCommand().also { it.resetContext() }
        val commands = root.registeredSubcommands().associateBy { it.commandName }
        val named = files.flatMap { file -> COMMAND.findAll(file.text).map { it.groupValues[1] to it.groupValues[2] } }
        assertTrue(named.size > 20, "only ${named.size} commands found")
        named.forEach { (name, next) ->
            val command = assertNotNull(commands[name], "the skill names kscope $name, which does not exist")
            assertSubcommand(command, next)
        }
    }

    private fun assertSubcommand(command: CoreCliktCommand, next: String) {
        val children = command.registeredSubcommands().map { it.commandName }
        if (children.isNotEmpty() && next.isNotEmpty()) {
            assertTrue(next in children, "the skill names kscope ${command.commandName} $next, which does not exist")
        }
    }

    private companion object {
        const val MAX_DESCRIPTION = 1024
        val COMMAND = Regex("(?:`|^\\s*)kscope ([a-z]+)(?: ([a-z]+))?", RegexOption.MULTILINE)
    }
}
