package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.service.registry.FolderOutcome.Reason
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactFolderRegistrationTest {
    private class Machine : AutoCloseable {
        val base: Path = Files.createTempDirectory("kscope-artifact-folders").toRealPath()
        val workspace: Path = Files.createDirectories(base.resolve("app"))
        val registryFile: Path = base.resolve("home").resolve(RepositoryRegistry.FILE_NAME)
        val registry = RepositoryRegistry(registryFile)
        val registration = ArtifactFolderRegistration(workspace, registry)

        fun folder(name: String): Path = Files.createDirectories(base.resolve(name))

        override fun close() {
            base.toFile().deleteRecursively()
        }
    }

    private fun reason(outcome: FolderOutcome) = (outcome as? FolderOutcome.Refused)?.reason

    @Test
    fun `a directory is registered by its real path, once`() = Machine().use { machine ->
        val m2 = machine.folder("m2")

        val outcome = machine.registration.add(m2)

        assertEquals(FolderOutcome.Registered(m2), outcome)
        assertEquals(listOf(m2), machine.registry.artifactFolders(machine.workspace))
        val again = machine.registration.add(m2.resolve("..").resolve("m2"))
        assertEquals(FolderOutcome.AlreadyRegistered(m2), again)
        assertEquals(listOf(m2), machine.registry.artifactFolders(machine.workspace))
    }

    @Test
    fun `a relative path is made absolute and normalized`() = Machine().use { machine ->
        val relative = Path.of("").toAbsolutePath().relativize(machine.folder("m2"))

        val outcome = machine.registration.add(relative)

        assertTrue(outcome is FolderOutcome.Registered && outcome.folder.isAbsolute, "$outcome")
        assertEquals(machine.base.resolve("m2"), outcome.folder)
    }

    @Test
    fun `a spelling that differs in case reaches the same folder only where the file system is case-insensitive`() =
        Machine().use { machine ->
            val folder = machine.folder("Maven")
            machine.registration.add(folder)
            val other = machine.base.resolve("maven")

            val outcome = machine.registration.add(other)

            if (Files.exists(other)) {
                assertEquals(FolderOutcome.AlreadyRegistered(folder), outcome)
            } else {
                assertEquals(Reason.NOT_A_DIRECTORY, reason(outcome))
            }
        }

    @Test
    fun `a directory that is missing or is a file is refused`() = Machine().use { machine ->
        val file = Files.writeString(machine.base.resolve("file"), "x")

        assertEquals(Reason.NOT_A_DIRECTORY, reason(machine.registration.add(machine.base.resolve("missing"))))
        assertEquals(Reason.NOT_A_DIRECTORY, reason(machine.registration.add(file)))
        assertEquals(emptyList(), machine.registration.list())
    }

    @Test
    fun `folders keep the order they were registered in`() = Machine().use { machine ->
        val first = machine.folder("zeta")
        val second = machine.folder("alpha")
        machine.registration.add(first)
        machine.registration.add(second)

        assertEquals(listOf(first, second), machine.registration.list().map { it.path })
    }

    @Test
    fun `list says when a folder is gone`() = Machine().use { machine ->
        val folder = machine.folder("m2")
        machine.registration.add(folder)
        folder.toFile().deleteRecursively()

        assertEquals(listOf(RegisteredFolder(folder, present = false)), machine.registration.list())
    }

    @Test
    fun `remove unregisters a folder, also one that is gone, and a second remove finds nothing`() =
        Machine().use { machine ->
            val folder = machine.folder("m2")
            machine.registration.add(folder)
            folder.toFile().deleteRecursively()

            assertEquals(FolderOutcome.Removed(folder), machine.registration.remove(folder))
            assertEquals(FolderOutcome.NotRegistered(folder), machine.registration.remove(folder))
            assertEquals(emptyList(), machine.registration.list())
        }

    @Test
    fun `Maven local is the repository folder of the Maven directory in the user home`() = Machine().use { machine ->
        val m2 = Files.createDirectories(machine.base.resolve("user/.m2/repository"))
        val properties = mapOf("user.home" to machine.base.resolve("user").toString())

        val outcome = machine.registration.addMavenLocal { properties[it] }

        assertEquals(FolderOutcome.Registered(m2), outcome)
    }

    @Test
    fun `Maven local that does not exist, or a home that is not known, is refused`() = Machine().use { machine ->
        val withoutRepository = mapOf("user.home" to machine.base.resolve("nobody").toString())

        assertEquals(Reason.NOT_A_DIRECTORY, reason(machine.registration.addMavenLocal { withoutRepository[it] }))
        assertEquals(Reason.NO_USER_HOME, reason(machine.registration.addMavenLocal { null }))
        assertEquals(Reason.NO_USER_HOME, reason(machine.registration.addMavenLocal { "" }))
    }

    @Test
    fun `a registry file that cannot be read refuses every change and leaves the file`() = Machine().use { machine ->
        val folder = machine.folder("m2")
        Files.createDirectories(machine.registryFile.parent)
        Files.writeString(machine.registryFile, "workspace = 3\n")

        assertEquals(Reason.UNREADABLE_REGISTRY, reason(machine.registration.add(folder)))
        assertEquals(Reason.UNREADABLE_REGISTRY, reason(machine.registration.remove(folder)))
        assertEquals("workspace = 3\n", Files.readString(machine.registryFile))
    }
}
