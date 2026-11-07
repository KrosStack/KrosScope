package io.github.krosstack.kscope.service.components

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ComponentInstallerSafetyTest : ComponentInstallerFixture() {
    @Test
    fun `offline archive is checked and extracted without network`() {
        val bytes = zip("data")
        val source = Files.write(root.resolve("offline.zip"), bytes)
        val installer = ComponentInstaller(ComponentDirectory(root.resolve("home"))) { error("network used") }
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        assertIs<InstallResult.Installed>(installer.install(entry, "linux-x64", InstallSource.Archive(source)))
        assertEquals("payload", Files.readString(root.resolve("home/sample/1/data")))
    }

    @Test
    fun `unmarked existing installation is preserved`() = server { installer ->
        val target = Files.createDirectories(root.resolve("sample/1"))
        Files.writeString(target.resolve("foreign"), "keep")
        assertIs<InstallResult.Unavailable>(installer.install(entry(zip("data")), "linux-x64", InstallSource.Network))
        assertEquals("keep", Files.readString(target.resolve("foreign")))
    }

    @Test
    fun `checksum failure preserves existing installation`() = server { installer ->
        val entry = entry(zip("runtime/data"))
        installer.install(entry, "linux-x64", InstallSource.Network)
        val bad = entry.copy(files = listOf(entry.files.last().copy(sha256 = "0".repeat(64))))
        assertIs<InstallResult.ChecksumMismatch>(installer.install(bad, "linux-x64", InstallSource.Network))
        assertEquals("payload", Files.readString(root.resolve("sample/1/runtime/data")))
        val names = Files.list(root.resolve("sample")).use { stream ->
            stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.toList()
        }
        assertEquals(listOf("1"), names)
    }

    @Test
    fun `mirror failure falls back to next pinned URL`() {
        val entry = entry(zip("data")).let {
            val file = it.files.last().copy(urls = listOf("https://mirror.test/file", "https://origin.test/file"))
            it.copy(files = listOf(file))
        }
        val installer = ComponentInstaller(ComponentDirectory(root)) {
            if (it.host == "mirror.test") throw java.io.IOException("unavailable")
            ByteArrayInputStream(plain)
        }
        assertIs<InstallResult.Installed>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEquals("component licence", Files.readString(root.resolve("sample/1/LICENSE")))
    }

    @Test
    fun `files for another platform are skipped`() {
        val entry = entry(zip("data")).let {
            it.copy(files = listOf(it.files.first().copy(platform = "macos-arm64"), it.files.last()))
        }
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(plain) }
        assertIs<InstallResult.Installed>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertFalse(Files.exists(root.resolve("sample/1/data")))
    }

    @Test
    fun `expanded ZIP bytes are bounded`() {
        val bytes = zip("data", "0".repeat(10000))
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(bytes) }
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        assertIs<InstallResult.Oversize>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEmpty()
    }

    @Test
    fun `drive letter ZIP path is rejected`() {
        val bytes = zip("C:/escape")
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(bytes) }
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        assertIs<InstallResult.UnsafeEntry>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEmpty()
    }

    @Test
    fun `directory created during download is not replaced without a marker`() {
        val installer = ComponentInstaller(ComponentDirectory(root)) {
            val foreign = Files.createDirectories(root.resolve("sample/1"))
            Files.writeString(foreign.resolve("foreign"), "keep")
            ByteArrayInputStream(plain)
        }
        val entry = entry(zip("data")).let { it.copy(files = it.files.takeLast(1)) }
        assertIs<InstallResult.Unavailable>(installer.install(entry, "linux-x64", InstallSource.Network))
        assertEquals("keep", Files.readString(root.resolve("sample/1/foreign")))
    }
}
