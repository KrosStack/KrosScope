package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.registry.RegistrationOutcome.Reason
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryRegistrationTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    private class Machine : AutoCloseable {
        val base: Path = Files.createTempDirectory("kscope-registration").toRealPath()
        val workspace: Path = repository("app")
        val registry = RepositoryRegistry(base.resolve("home").resolve(RepositoryRegistry.FILE_NAME))
        val registration = RepositoryRegistration(workspace, registry)

        fun repository(name: String, commit: Boolean = true): Path {
            val directory = Files.createDirectories(base.resolve(name))
            Files.writeString(directory.resolve("README.md"), "$name\n")
            TestGit.run(directory, listOf("init", "-q"))
            if (commit) {
                TestGit.run(directory, listOf("add", "-A"))
                TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
            }
            return directory
        }

        override fun close() {
            base.toFile().deleteRecursively()
        }
    }

    private fun Machine.reason(outcome: RegistrationOutcome) = (outcome as? RegistrationOutcome.Refused)?.reason

    @Test
    fun `a committed checkout elsewhere is registered under its folder name, once`() = Machine().use { machine ->
        val kit = machine.repository("kit")
        val outcome = machine.registration.add(kit.toString())

        assertTrue(outcome is RegistrationOutcome.Registered && outcome.repository.name == "kit", "$outcome")
        assertEquals(kit.toRealPath(), machine.registry.registered(machine.workspace).single().root)
        val again = machine.registration.add(kit.resolve("subdirectory-does-not-matter").parent.toString())
        assertTrue(again is RegistrationOutcome.AlreadyRegistered, "$again")
    }

    @Test
    fun `a directory that is not a committed checkout outside the workspace is refused`() = Machine().use { machine ->
        val plain = Files.createDirectories(machine.base.resolve("plain"))
        val empty = machine.repository("empty", commit = false)
        val inside = Files.createDirectories(machine.workspace.resolve("nested"))

        assertEquals(Reason.NOT_A_REPOSITORY, machine.reason(machine.registration.add(plain.toString())))
        assertEquals(Reason.WITHOUT_COMMIT, machine.reason(machine.registration.add(empty.toString())))
        assertEquals(Reason.INSIDE_WORKSPACE, machine.reason(machine.registration.add(inside.toString())))
        assertEquals(emptyList(), machine.registration.list())
    }

    @Test
    fun `a name must be valid and free in the workspace root`() = Machine().use { machine ->
        val kit = machine.repository("kit")
        Files.createDirectories(machine.workspace.resolve("docs"))

        assertEquals(Reason.INVALID_NAME, machine.reason(machine.registration.add(kit.toString(), name = ".hidden")))
        assertEquals(Reason.NAME_TAKEN, machine.reason(machine.registration.add(kit.toString(), name = "docs")))
        val renamed = machine.registration.add(kit.toString(), name = "shared-kit")
        assertTrue(renamed is RegistrationOutcome.Registered && renamed.repository.name == "shared-kit", "$renamed")
    }

    @Test
    fun `removing unregisters by name and leaves the checkout`() = Machine().use { machine ->
        val kit = machine.repository("kit")
        machine.registration.add(kit.toString())

        assertTrue(machine.registration.remove("kit") is RegistrationOutcome.Removed)
        assertTrue(machine.registration.remove("kit") is RegistrationOutcome.NotRegistered)
        assertTrue(Files.isDirectory(kit))
        assertEquals(emptyList(), machine.registration.list())
    }

    @Test
    fun `a Git URL is cloned beside the workspace and registered`() = Machine().use { machine ->
        val origin = machine.repository("origin-kit")
        val url = origin.toUri().toString()
        val outcome = machine.registration.add(url, name = "kit")

        assertTrue(outcome is RegistrationOutcome.Registered && outcome.cloned, "$outcome")
        assertTrue(Files.isRegularFile(machine.base.resolve("kit/README.md")))
        assertEquals(listOf(true), machine.registration.list().map { it.present })
        assertEquals(Reason.DIRECTORY_EXISTS, machine.reason(machine.registration.add(url, name = "kit")))
    }

    @Test
    fun `an unreadable list of registered repositories refuses every change`() = Machine().use { machine ->
        val kit = machine.repository("kit")
        Files.createDirectories(machine.base.resolve("home"))
        Files.writeString(machine.base.resolve("home").resolve(RepositoryRegistry.FILE_NAME), "not toml [\n")

        assertEquals(Reason.UNREADABLE_REGISTRY, machine.reason(machine.registration.add(kit.toString())))
        assertEquals(Reason.UNREADABLE_REGISTRY, machine.reason(machine.registration.remove("kit")))
    }
}
