package io.github.krosstack.kscope.cli.skill

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SkillInstallerTest {
    private val files = listOf(
        SkillFile("SKILL.md", "---\nname: kscope\ndescription: test\n---\nbody one\n"),
        SkillFile("references/commands.md", "commands\n"),
    )

    private fun <T> withFolder(block: (Path) -> T): T {
        val folder = Files.createTempDirectory("kscope-skill-folder")
        try {
            return block(folder.resolve("kscope"))
        } finally {
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun `installing writes every file of the skill`() = withFolder { directory ->
        val outcome = SkillInstaller(files).install(directory)

        assertEquals(SkillOutcome.Installed(directory), outcome)
        assertEquals(files[0].text, Files.readString(directory.resolve("SKILL.md")))
        assertEquals(files[1].text, Files.readString(directory.resolve("references/commands.md")))
        assertEquals(listOf("kscope"), namesIn(directory.parent))
    }

    private fun namesIn(folder: Path): List<String> = Files.list(folder).use { paths ->
        paths.map { it.fileName.toString() }.toList()
    }

    @Test
    fun `installing again replaces the skill and drops files it no longer has`() = withFolder { directory ->
        SkillInstaller(files).install(directory)
        Files.writeString(directory.resolve("stale.md"), "old")
        val newer = listOf(SkillFile("SKILL.md", "---\nname: kscope\ndescription: test\n---\nbody two\n"))

        val outcome = SkillInstaller(newer).install(directory)

        assertEquals(SkillOutcome.Updated(directory), outcome)
        assertTrue(Files.readString(directory.resolve("SKILL.md")).contains("body two"))
        assertFalse(Files.exists(directory.resolve("stale.md")))
        assertFalse(Files.exists(directory.resolve("references")))
    }

    @Test
    fun `a skill of the same name from elsewhere is left alone`() = withFolder { directory ->
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("SKILL.md"), "---\nname: other\ndescription: someone else's\n---\n")

        assertIs<SkillOutcome.Foreign>(SkillInstaller(files).install(directory))
        assertIs<SkillOutcome.Foreign>(SkillInstaller(files).uninstall(directory))
        assertTrue(Files.readString(directory.resolve("SKILL.md")).contains("name: other"))
    }

    @Test
    fun `a folder with other files and no skill is left alone`() = withFolder { directory ->
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("notes.txt"), "keep")

        assertIs<SkillOutcome.Foreign>(SkillInstaller(files).install(directory))
        assertEquals("keep", Files.readString(directory.resolve("notes.txt")))
    }

    @Test
    fun `an empty folder is taken`() = withFolder { directory ->
        Files.createDirectories(directory)

        assertEquals(SkillOutcome.Updated(directory), SkillInstaller(files).install(directory))
        assertTrue(Files.exists(directory.resolve("SKILL.md")))
    }

    @Test
    fun `a link in place of the folder is never followed`() = withFolder { directory ->
        val elsewhere = Files.createDirectories(directory.resolveSibling("elsewhere"))
        Files.writeString(elsewhere.resolve("SKILL.md"), files[0].text)
        val linked = try {
            Files.createSymbolicLink(directory, elsewhere)
            true
        } catch (_: IOException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
        if (linked) {
            assertIs<SkillOutcome.Foreign>(SkillInstaller(files).install(directory))
            assertIs<SkillOutcome.Foreign>(SkillInstaller(files).uninstall(directory))
            assertTrue(Files.exists(elsewhere.resolve("SKILL.md")))
        }
    }

    @Test
    fun `uninstalling removes the skill and says when there is none`() = withFolder { directory ->
        SkillInstaller(files).install(directory)

        assertEquals(SkillOutcome.Removed(directory), SkillInstaller(emptyList()).uninstall(directory))
        assertFalse(Files.exists(directory))
        assertEquals(SkillOutcome.Absent(directory), SkillInstaller(emptyList()).uninstall(directory))
    }
}
