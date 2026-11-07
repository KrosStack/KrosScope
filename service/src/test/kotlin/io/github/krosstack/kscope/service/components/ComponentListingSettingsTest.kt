package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.settings.InvalidSettingsException
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComponentListingSettingsTest {
    @TempDir
    lateinit var root: Path
    private val entry = ComponentEntry(
        "sample",
        "1",
        "Apache-2.0",
        "LICENSE",
        "Sample",
        listOf(ComponentFile("data", "linux-x64", listOf("https://example.test/data"), 1, "0".repeat(64))),
    )

    @Test
    fun `workspace false disables an installed component`() {
        mark("1")
        Files.writeString(root.resolve("kscope.toml"), "[components]\nsample = false\n")
        assertEquals(ComponentState.DISABLED, state(WorkspaceSettings.read(root)))
    }

    @Test
    fun `non boolean component setting is refused`() {
        Files.writeString(root.resolve("kscope.toml"), "[components]\nsample = 1\n")
        assertFailsWith<InvalidSettingsException> { WorkspaceSettings.read(root) }
    }

    @Test
    fun `nested component setting is refused`() {
        Files.writeString(root.resolve("kscope.toml"), "[components.sample]\nunknown = false\n")
        assertFailsWith<InvalidSettingsException> { WorkspaceSettings.read(root) }
    }

    @Test
    fun `component names with dots can be disabled`() {
        Files.writeString(root.resolve("kscope.toml"), "[components]\n\"sample.name\" = false\n")
        assertEquals(false, WorkspaceSettings.read(root).components["sample.name"])
    }

    private fun state(settings: WorkspaceSettings = WorkspaceSettings(), platform: String = "linux-x64") =
        ComponentManifest(1, listOf(entry)).listing(ComponentDirectory(root), platform, settings).single().state

    private fun mark(version: String) {
        InstalledComponent.write(
            InstalledComponent(
                "sample",
                version,
                Files.createDirectories(root.resolve("sample/$version")),
                listOf("data"),
            ),
        )
    }
}
