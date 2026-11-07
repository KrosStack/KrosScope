package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentEntry
import io.github.krosstack.kscope.service.components.ComponentFile
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.InstalledComponent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentsTextTest {
    @TempDir
    lateinit var root: Path

    private val manifest = ComponentManifest(
        1,
        listOf(
            ComponentEntry(
                "sample",
                "1",
                "Apache-2.0",
                "LICENSE",
                "Sample component",
                listOf(ComponentFile("data", "any", listOf("https://example.test/data"), 7, "0".repeat(64))),
            ),
        ),
    )

    @Test
    fun `list text prints two entries in different states`() {
        mark("sample", "1")
        val entries = manifest.components + manifest.components.single().copy(name = "other")
        val fixture = manifest.copy(components = entries)
        val result = ComponentsCommand(fixture, ComponentDirectory(root.resolve("home")))
            .test(listOf("list", "--workspace", root.toString()))
        assertEquals(
            "other 1  Apache-2.0  7 bytes  not installed\nsample 1  Apache-2.0  7 bytes  installed\n",
            result.stdout,
        )
    }

    @Test
    fun `status text prints installed version and interrupted backup`() {
        val installed = mark("sample", "1")
        val backup = mark("other", "2", "2.replacing-old")
        val result = command().test(listOf("status"))
        assertEquals(
            "other 2  $backup  interrupted install (backup kept)\n" +
                "sample 1  $installed  matches manifest: true\n",
            result.stdout,
        )
    }

    @Test
    fun `status text explains corrupt marker`() {
        val target = mark("sample", "1")
        Files.writeString(target.resolve("kscope-component.json"), "invalid json")
        val result = command().test(listOf("status"))
        assertEquals("sample 1  $target  corrupt installation: Invalid component marker\n", result.stdout)
    }

    @Test
    fun `status reports malformed JSON marker as damaged`() {
        val target = mark("sample", "1")
        Files.writeString(target.resolve("kscope-component.json"), "{")
        assertEquals(
            "sample 1  $target  corrupt installation: Invalid component marker\n",
            command().test(listOf("status")).stdout,
        )
    }

    @Test
    fun `status keeps partially cleaned backup visible`() {
        val backup = mark("sample", "1", "1.replacing-old")
        Files.delete(backup.resolve("kscope-component.json"))
        assertEquals(
            "sample 1  $backup  interrupted install (backup kept)\n",
            command().test(listOf("status")).stdout,
        )
    }

    @Test
    fun `remove success text names removed component`() {
        mark("sample", "1")
        assertEquals("Removed sample.\n", command().test(listOf("remove", "sample")).stdout)
    }

    @Test
    fun `remove refusal text explains unmarked directory`() {
        Files.createDirectories(root.resolve("home/sample/1"))
        assertEquals(
            "Refused: Component directory is not marked for sample\n",
            command().test(listOf("remove", "sample")).stderr,
        )
    }

    private fun mark(name: String, version: String, folder: String = version): Path {
        val target = Files.createDirectories(root.resolve("home/$name/$folder"))
        InstalledComponent.write(InstalledComponent(name, version, target, listOf("data")))
        return target
    }

    private fun command() = ComponentsCommand(manifest, ComponentDirectory(root.resolve("home")))
}
