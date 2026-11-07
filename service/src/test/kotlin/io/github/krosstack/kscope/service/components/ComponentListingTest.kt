package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentListingTest {
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
    fun `marked matching version is installed`() {
        mark("1")
        assertEquals(ComponentState.INSTALLED, state())
    }

    @Test
    fun `missing component is not installed`() {
        assertEquals(ComponentState.NOT_INSTALLED, state())
    }

    @Test
    fun `corrupt marker is listed as a corrupt installation`() {
        val target = Files.createDirectories(root.resolve("sample/1"))
        Files.writeString(target.resolve("kscope-component.json"), "invalid json")
        val row = ComponentManifest(1, listOf(entry))
            .listing(ComponentDirectory(root), "linux-x64", WorkspaceSettings()).single()
        assertEquals("corrupt installation", row.state.text)
        assertEquals(target, row.installed?.directory)
    }

    @Test
    fun `marker naming another component is listed as corrupt`() {
        mark("1")
        val target = root.resolve("sample/1")
        InstalledComponent.write(InstalledComponent("foreign", "1", target, emptyList()))
        assertEquals("corrupt installation", state().text)
    }

    @Test
    fun `different installed version is reported`() {
        mark("2")
        assertEquals(ComponentState.OTHER_VERSION, state())
    }

    @Test
    fun `component without matching files is unsupported`() {
        assertEquals(ComponentState.UNSUPPORTED, state(platform = "macos-arm64"))
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
