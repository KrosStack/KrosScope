package io.github.krosstack.kscope.service.components

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ComponentInstallerDeletionTest : ComponentInstallerFixture() {
    @Test
    fun `ZIP symbolic link is unpacked as a plain file`() {
        val bytes = zip("link")
        val central = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = (0..bytes.size - 4).first { central.getInt(it) == 0x02014b50 }
        central.putInt(offset + 38, 0xa1ff shl 16)
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(bytes) }
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        assertIs<InstallResult.Installed>(installer.install(entry, "linux-x64", InstallSource.Network))
        val target = root.resolve("sample/1/link")
        assertEquals("payload", Files.readString(target))
        assertFalse(Files.isSymbolicLink(target))
    }

    @Test
    fun `ZIP symbolic link with forged entry count is unpacked as a plain file`() {
        val bytes = zip("link")
        val central = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = (0..bytes.size - 4).first { central.getInt(it) == 0x02014b50 }
        central.putInt(offset + 38, 0xa1ff shl 16)
        central.putShort(bytes.size - 22 + 8, 0)
        central.putShort(bytes.size - 22 + 10, 0)
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(bytes) }
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        assertIs<InstallResult.Installed>(installer.install(entry, "linux-x64", InstallSource.Network))
        val target = root.resolve("sample/1/link")
        assertEquals("payload", Files.readString(target))
        assertFalse(Files.isSymbolicLink(target))
    }

    @Test
    fun `committed replacement succeeds when backup cannot be deleted`() {
        val target = Files.createDirectories(root.resolve("sample/1"))
        InstalledComponent.write(InstalledComponent("sample", "1", target, listOf("held")))
        val entry = entry(zip("data")).let { it.copy(files = it.files.takeLast(1)) }
        val installer = ComponentInstaller(ComponentDirectory(root)) { ByteArrayInputStream(plain) }
        val installed = withBlockedDeletion(target) {
            installer.install(entry, "linux-x64", InstallSource.Network)
        }
        val result = assertIs<InstallResult.Replaced>(installed, installed.toString())
        val held = result.retainedBackup?.resolve("held")?.let(Files::readString)
        assertEquals("old" to "component licence", held to Files.readString(target.resolve("LICENSE")))
    }

    private fun withBlockedDeletion(target: Path, action: () -> InstallResult): InstallResult {
        val held = Files.writeString(target.resolve("held"), "old")
        if (System.getProperty("os.name").startsWith("Windows")) {
            Files.setAttribute(held, "dos:readonly", true)
            var result: InstallResult? = null
            try {
                return action().also { result = it }
            } finally {
                val backup = (result as? InstallResult.Replaced)?.retainedBackup?.resolve("held")
                if (backup != null && Files.exists(backup)) Files.setAttribute(backup, "dos:readonly", false)
                if (Files.exists(held)) Files.setAttribute(held, "dos:readonly", false)
            }
        }
        val permissions = Files.getPosixFilePermissions(target)
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r-x------"))
        try {
            assumeTrue(!Files.isWritable(target), "Directory remains writable")
            return action()
        } finally {
            Files.list(target.parent).use { paths ->
                paths.forEach { Files.setPosixFilePermissions(it, permissions) }
            }
        }
    }
}
