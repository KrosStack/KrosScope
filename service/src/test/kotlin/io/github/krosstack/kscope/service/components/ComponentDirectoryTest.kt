package io.github.krosstack.kscope.service.components

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComponentDirectoryTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `remove deletes marked directory`() {
        val target = Files.createDirectories(root.resolve("sample/1"))
        InstalledComponent.write(InstalledComponent("sample", "1", target, emptyList()))
        assertEquals(RemoveResult.Removed, ComponentDirectory(root).remove("sample"))
        assertFalse(Files.exists(target))
    }

    @Test
    fun `lower case component root lists and removes installed component`() {
        val absolute = root.toAbsolutePath()
        val lowerCaseRoot = Path.of(absolute.root.toString().lowercase()).resolve(
            absolute.mapIndexed { index, segment ->
                if (index == 0) segment.toString().lowercase() else segment.toString()
            }.joinToString("/"),
        )
        assumeTrue(Files.exists(lowerCaseRoot), "File system is case sensitive")
        val target = Files.createDirectories(root.resolve("sample/1"))
        InstalledComponent.write(InstalledComponent("sample", "1", target, listOf("data")))
        Files.writeString(target.resolve("data"), "payload")
        val directory = ComponentDirectory(lowerCaseRoot)
        assertEquals(listOf("sample"), directory.installed().map { it.name })
        assertEquals(RemoveResult.Removed, directory.remove("sample"))
        assertFalse(Files.exists(target))
    }

    @Test
    fun `remove refuses unmarked directory`() {
        Files.createDirectories(root.resolve("sample/1"))
        assertIs<RemoveResult.Refused>(ComponentDirectory(root).remove("sample"))
    }

    @Test
    fun `remove reports missing directory`() {
        assertEquals(RemoveResult.NotInstalled, ComponentDirectory(root).remove("sample"))
    }

    @Test
    fun `remove refuses corrupt marker`() {
        val target = Files.createDirectories(root.resolve("sample/1"))
        Files.writeString(target.resolve("kscope-component.json"), "invalid json")
        assertIs<RemoveResult.Refused>(ComponentDirectory(root).remove("sample"))
    }

    @Test
    fun `remove refuses marker for another component`() {
        val target = Files.createDirectories(root.resolve("sample/1"))
        InstalledComponent.write(InstalledComponent("foreign", "1", target, emptyList()))
        assertIs<RemoveResult.Refused>(ComponentDirectory(root).remove("sample"))
    }

    @Test
    fun `remove refuses an empty unmarked name directory`() {
        Files.createDirectories(root.resolve("sample"))
        assertIs<RemoveResult.Refused>(ComponentDirectory(root).remove("sample"))
    }

    @Test
    fun `remove deletes a directory link without touching its target`() {
        val target = Files.createDirectories(root.resolve("sample/1"))
        InstalledComponent.write(InstalledComponent("sample", "1", target, emptyList()))
        val sibling = Files.createDirectory(root.resolve("sibling"))
        val kept = Files.writeString(sibling.resolve("keep"), "keep")
        val link = target.resolve("link")
        link(link, sibling)
        assertEquals(RemoveResult.Removed, ComponentDirectory(root).remove("sample"))
        assertFalse(Files.exists(target))
        assertTrue(Files.exists(kept))
    }

    @Test
    fun `remove refuses a version linked outside the component directory`() {
        Files.createDirectory(root.resolve("sample"))
        val sibling = Files.createDirectory(root.resolve("sibling"))
        val kept = Files.writeString(sibling.resolve("keep"), "keep")
        val version = root.resolve("sample/1")
        link(version, sibling)
        try {
            assertEquals(
                RemoveResult.Refused("Path outside component directory: $version"),
                ComponentDirectory(root).remove("sample"),
            )
            assertTrue(Files.exists(kept))
        } finally {
            Files.delete(version)
        }
    }

    @Test
    fun `remove restores interrupted backup before removing it and stale staging`() {
        val backup = Files.createDirectories(root.resolve("sample/1.replacing-old"))
        InstalledComponent.write(InstalledComponent("sample", "1", backup, emptyList()))
        val staging = Files.createDirectories(root.resolve("sample/1.installing-old"))
        Files.writeString(staging.resolve("kscope-staging.json"), """{"name":"sample","version":"1"}""")
        assertEquals(RemoveResult.Removed, ComponentDirectory(root).remove("sample"))
        assertEquals(emptyList(), ComponentDirectory(root).installed())
    }

    private fun link(link: Path, target: Path) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).start()
            assumeTrue(process.waitFor() == 0, "Junction creation unavailable")
        } else {
            Files.createSymbolicLink(link, target)
        }
    }
}
