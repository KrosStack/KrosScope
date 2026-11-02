package io.github.krosstack.kscope.cli.workspace

import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceArtifactsRefusalTest {
    @Test
    fun `a directory that is missing or is a file is refused with exit code 1`() = ArtifactsMachine().use { machine ->
        val file = Files.writeString(machine.base.resolve("file"), "x")

        val missing = machine.run("add", machine.base.resolve("missing").toString())
        val notDirectory = machine.run("add", file.toString())

        assertEquals(1, missing.statusCode)
        assertTrue(missing.stderr.contains("is not a directory"), missing.stderr)
        assertEquals(1, notDirectory.statusCode)
        assertTrue(machine.run("list").stdout.startsWith("No artifact folder is registered"))
    }

    @Test
    fun `a tilde that the shell did not expand is not expanded here either`() = ArtifactsMachine().use { machine ->
        val refused = machine.run("add", "~/.m2/repository")

        assertEquals(1, refused.statusCode)
        assertTrue(refused.stderr.contains("is not a directory"), refused.stderr)
    }

    @Test
    fun `maven-local without a repository folder is refused`() = ArtifactsMachine().use { machine ->
        val refused = machine.run("add", "--maven-local")

        assertEquals(1, refused.statusCode)
        assertTrue(refused.stderr.contains("is not a directory"), refused.stderr)
    }

    @Test
    fun `add needs a directory or maven-local, and not both`() = ArtifactsMachine().use { machine ->
        val neither = machine.run("add")
        val both = machine.run("add", machine.folder("m2").toString(), "--maven-local")

        assertEquals(2, neither.statusCode)
        assertTrue(neither.stderr.contains("--maven-local"), neither.stderr)
        assertEquals(2, both.statusCode)
        assertTrue(both.stderr.contains("--maven-local"), both.stderr)
    }

    @Test
    fun `remove unregisters a folder and a second remove fails`() = ArtifactsMachine().use { machine ->
        val m2 = machine.folder("m2")
        machine.run("add", m2.toString())

        val removed = machine.run("remove", m2.toString())
        assertEquals(0, removed.statusCode, removed.output)
        assertTrue(removed.stdout.startsWith("Unregistered ${machine.slashed(m2)}"), removed.stdout)
        val again = machine.run("remove", m2.toString())

        assertEquals(1, again.statusCode)
        assertTrue(again.stderr.contains("is not registered as an artifact folder"), again.stderr)
    }

    @Test
    fun `a folder that was deleted can still be removed by its path`() = ArtifactsMachine().use { machine ->
        val m2 = machine.folder("m2")
        machine.run("add", m2.toString())
        m2.toFile().deleteRecursively()

        val removed = machine.run("remove", m2.toString())

        assertEquals(0, removed.statusCode, removed.output)
        assertTrue(machine.run("list").stdout.startsWith("No artifact folder is registered"))
    }

    @Test
    fun `a workspace given to the artifacts group is the one that add registers with`() =
        ArtifactsMachine().use { machine ->
            val arguments = listOf("artifacts", "--workspace", "${machine.app}", "add", "${machine.folder("m2")}")

            val added = machine.execute(arguments)

            assertEquals(0, added.statusCode, added.output)
            assertTrue(machine.slashed(machine.app) in added.stdout, added.stdout)
        }

    @Test
    fun `a registry file that cannot be read refuses the change and exits with 1`() =
        ArtifactsMachine().use { machine ->
            Files.createDirectories(machine.registryFile.parent)
            Files.writeString(machine.registryFile, "workspace = 3\n")

            val refused = machine.run("add", machine.folder("m2").toString())

            assertEquals(1, refused.statusCode)
            assertTrue(refused.stderr.contains(RepositoryRegistry.FILE_NAME), refused.stderr)
        }

    @Test
    fun `the group prints its help when no command is given`() = ArtifactsMachine().use { machine ->
        val help = machine.execute(listOf("artifacts"))

        assertTrue(help.output.contains("add") && help.output.contains("remove") && help.output.contains("list"))
    }
}
