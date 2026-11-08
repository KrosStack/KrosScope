package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComponentPlatformTest : ComponentInstallerFixture() {
    private val manifest = ComponentManifest.read(Files.readString(Path.of("src/main/resources/components.json")))

    @Test
    fun `semantic components are unsupported on Intel macOS`() {
        val rows = manifest.listing(ComponentDirectory(root), "macos-x64", WorkspaceSettings())
        assertEquals(
            mapOf("semantic" to ComponentState.UNSUPPORTED, "semantic-model" to ComponentState.UNSUPPORTED),
            rows.associate { it.entry.name to it.state },
        )
    }

    @Test
    fun `semantic installs refuse Intel macOS before reading their source`() {
        val installer = ComponentInstaller(ComponentDirectory(root)) { error("Unsupported source was read") }
        assertEquals(
            listOf(
                InstallResult.Unavailable("semantic", "Unsupported platform"),
                InstallResult.Unavailable("semantic-model", "Unsupported platform"),
            ),
            manifest.components.map { installer.install(it, "macos-x64", InstallSource.Network) },
        )
    }

    @Test
    fun `both shipped components validate with exactly four semantic platforms`() {
        val platforms = listOf("windows-x64", "linux-x64", "linux-aarch64", "macos-arm64")
        assertEquals(
            mapOf("semantic" to platforms, "semantic-model" to platforms),
            manifest.components.associate { it.name to it.platforms },
        )
    }

    @TestFactory
    fun `semantic listing rejects other unsupported platforms`() = listOf(
        "windows-arm64",
        "linux-x86",
        "unsupported",
    ).map { platform ->
        dynamicTest(platform) {
            val rows = manifest.listing(ComponentDirectory(root), platform, WorkspaceSettings())
            assertEquals(
                mapOf("semantic" to ComponentState.UNSUPPORTED, "semantic-model" to ComponentState.UNSUPPORTED),
                rows.associate { it.entry.name to it.state },
            )
        }
    }

    @TestFactory
    fun `declared platforms still install shared files`() = listOf(
        "windows-x64",
        "linux-x64",
        "linux-aarch64",
        "macos-arm64",
    ).map { platform ->
        dynamicTest(platform) {
            val home = root.resolve(platform)
            val installer = ComponentInstaller(ComponentDirectory(home)) { ByteArrayInputStream(plain) }
            val entry = entry(zip("data")).let {
                it.copy(
                    files = listOf(it.files.last()),
                    platforms = manifest.components.single { component -> component.name == "semantic" }.platforms,
                )
            }
            installer.install(entry, platform, InstallSource.Network)
            assertEquals("component licence", Files.readString(home.resolve("sample/1/LICENSE")))
        }
    }

    @TestFactory
    fun `declared platforms still list shared files as available`() = listOf(
        "windows-x64",
        "linux-x64",
        "linux-aarch64",
        "macos-arm64",
    ).map { platform ->
        dynamicTest(platform) {
            val rows = manifest.listing(ComponentDirectory(root), platform, WorkspaceSettings())
            assertEquals(
                mapOf("semantic" to ComponentState.NOT_INSTALLED, "semantic-model" to ComponentState.NOT_INSTALLED),
                rows.associate { it.entry.name to it.state },
            )
        }
    }

    @TestFactory
    fun `invalid component platform declarations are rejected`() = listOf(
        emptyList(),
        listOf("unknown"),
        listOf("any"),
        listOf("linux-x64", "linux-x64"),
    ).map { platforms ->
        dynamicTest(platforms.toString()) {
            assertFailsWith<IllegalArgumentException> {
                ComponentManifest.validate(entry(zip("data")).copy(platforms = platforms))
            }
        }
    }

    @TestFactory
    fun `component and file platforms must agree`() = listOf<Pair<String, (ComponentEntry) -> ComponentEntry>>(
        "file outside the declared platforms" to {
            it.copy(
                files = listOf(it.files.first(), it.files.last().copy(platform = "windows-x64")),
                platforms = listOf("linux-x64"),
            )
        },
        "declared platform without a payload" to {
            it.copy(
                files = listOf(it.files.last().copy(platform = "windows-x64")),
                platforms = listOf("linux-x64", "windows-x64"),
            )
        },
    ).map { (case, change) ->
        dynamicTest(case) {
            assertFailsWith<IllegalArgumentException> { ComponentManifest.validate(change(entry(zip("data")))) }
        }
    }

    @TestFactory
    fun `platform detection matches semantic native directories`() = listOf(
        Triple("Windows 11", "amd64", "windows-x64"),
        Triple("Linux", "x86_64", "linux-x64"),
        Triple("Linux", "aarch64", "linux-aarch64"),
        Triple("Linux", "arm64", "linux-aarch64"),
        Triple("Mac OS X", "arm64", "macos-arm64"),
        Triple("Mac OS X", "x86_64", "macos-x64"),
        Triple("Windows 11", "aarch64", "unsupported"),
        Triple("Linux", "x86", "unsupported"),
    ).map { (os, arch, platform) ->
        dynamicTest("$os $arch") { assertEquals(platform, ComponentPlatform.current(os, arch)) }
    }
}
