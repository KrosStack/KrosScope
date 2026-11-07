package io.github.krosstack.kscope.service.components

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ComponentInstallerTest : ComponentInstallerFixture() {
    @Test
    fun `installs plain file and zip with marker`() = server { installer ->
        val result = installer.install(entry(zip("runtime/data")), "linux-x64", InstallSource.Network)
        assertIs<InstallResult.Installed>(result)
        assertEquals("payload", Files.readString(root.resolve("sample/1/runtime/data")))
        assertEquals(listOf("bundle.zip", "LICENSE"), ComponentDirectory(root).installed("sample")?.files)
    }

    @Test
    fun `checksum mismatch leaves no installation or staging directory`() = server { installer ->
        val entry = entry(zip("data")).let { it.copy(files = listOf(it.files.last().copy(sha256 = "0".repeat(64)))) }
        assertIs<InstallResult.ChecksumMismatch>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEmpty()
    }

    @Test
    fun `oversize leaves no installation`() = server { installer ->
        val entry = entry(zip("data")).let { it.copy(files = listOf(it.files.last().copy(size = 1))) }
        assertIs<InstallResult.Oversize>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEmpty()
    }

    @Test
    fun `parent zip entry leaves no installation`() {
        val bytes = zip("../escape")
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(bytes) }
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        assertIs<InstallResult.UnsafeEntry>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEmpty()
        assertFalse(Files.exists(root.resolve("sample/escape")))
    }

    @Test
    fun `second install replaces marked version`() = server { installer ->
        val entry = entry(zip("runtime/data"))
        installer.install(entry, "linux-x64", InstallSource.Network)
        Files.writeString(root.resolve("sample/1/stale"), "old")
        assertIs<InstallResult.Replaced>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertFalse(Files.exists(root.resolve("sample/1/stale")))
    }

    @Test
    fun `directory source installs without network`() {
        val source = Files.createDirectories(root.resolve("offline"))
        val bytes = zip("data")
        Files.write(source.resolve("bundle.zip"), bytes)
        Files.write(source.resolve("LICENSE"), plain)
        val installer = ComponentInstaller(ComponentDirectory(root.resolve("home"))) { error("network used") }
        assertIs<InstallResult.Installed>(installer.install(entry(bytes), "linux-x64", InstallSource.Directory(source)))
    }

    @Test
    fun `redirect is followed`() = server { installer ->
        val entry = entry(zip("data")).let {
            it.copy(files = listOf(it.files.last().copy(urls = listOf("https://example.test/redirect"))))
        }
        assertIs<InstallResult.Installed>(installer.install(entry, "linux-x64", InstallSource.Network))
    }

    @Test
    fun `install restores interrupted backup before a failed download`() {
        val backup = Files.createDirectories(root.resolve("sample/1.replacing-old"))
        InstalledComponent.write(InstalledComponent("sample", "1", backup, listOf("old")))
        Files.writeString(backup.resolve("old"), "keep")
        val staging = Files.createDirectories(root.resolve("sample/1.installing-old"))
        Files.writeString(staging.resolve("kscope-staging.json"), """{"name":"sample","version":"1"}""")
        Files.writeString(staging.resolve("partial"), "partial")
        val installer = ComponentInstaller(ComponentDirectory(root)) { throw java.io.IOException("offline") }
        assertIs<InstallResult.Unavailable>(installer.install(entry(zip("data")), "linux-x64", InstallSource.Network))
        assertEquals("keep", Files.readString(root.resolve("sample/1/old")))
        assertFalse(Files.exists(backup))
        assertFalse(Files.exists(staging))
    }
}
