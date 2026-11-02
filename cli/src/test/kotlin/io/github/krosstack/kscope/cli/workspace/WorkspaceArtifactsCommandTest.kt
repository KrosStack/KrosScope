package io.github.krosstack.kscope.cli.workspace

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceArtifactsCommandTest {
    @Test
    fun `add registers a folder and list shows it`() = ArtifactsMachine().use { machine ->
        val m2 = machine.folder("m2")

        val added = machine.run("add", m2.toString())

        assertEquals(0, added.statusCode, added.output)
        assertTrue(added.stdout.startsWith("Registered ${machine.slashed(m2)} as an artifact folder"), added.stdout)
        assertTrue(machine.slashed(machine.app) in added.stdout, added.stdout)
        val listed = machine.run("list")
        assertEquals(0, listed.statusCode, listed.output)
        assertTrue(listed.stdout.startsWith("1 artifact folder registered with the workspace at"), listed.stdout)
        assertTrue(machine.slashed(m2) in listed.stdout, listed.stdout)
    }

    @Test
    fun `list says so when nothing is registered and when a folder is gone`() = ArtifactsMachine().use { machine ->
        val empty = machine.run("list").stdout
        assertTrue(empty.startsWith("No artifact folder is registered"), empty)

        val gone = machine.folder("gone")
        machine.run("add", gone.toString())
        gone.toFile().deleteRecursively()

        val listed = machine.run("list").stdout
        assertTrue(listed.contains("(missing)"), listed)
    }

    @Test
    fun `list prints the folders in the order they are looked in`() = ArtifactsMachine().use { machine ->
        val second = machine.folder("zeta")
        val first = machine.folder("alpha")
        machine.run("add", second.toString())
        machine.run("add", first.toString())

        val lines = machine.run("list").stdout.lines().filter { it.startsWith("  ") }

        assertEquals(listOf(machine.slashed(second), machine.slashed(first)), lines.map { it.trim() })
    }

    @Test
    fun `registering a folder twice says so and changes nothing`() = ArtifactsMachine().use { machine ->
        val m2 = machine.folder("m2")
        machine.run("add", m2.toString())

        val again = machine.run("add", m2.resolve("..").resolve("m2").toString())

        assertEquals(0, again.statusCode, again.output)
        assertTrue(again.stdout.contains("is registered already"), again.stdout)
        assertTrue(machine.run("list").stdout.startsWith("1 artifact folder registered"))
    }

    @Test
    fun `maven-local registers the repository folder below the user home`() = ArtifactsMachine().use { machine ->
        val m2 = Files.createDirectories(machine.home.resolve(".m2").resolve("repository"))

        val added = machine.run("add", "--maven-local")

        assertEquals(0, added.statusCode, added.output)
        assertTrue(machine.slashed(m2) in added.stdout, added.stdout)
        assertTrue(machine.slashed(m2) in machine.run("list").stdout)
    }

    @Test
    fun `folders are kept per workspace`() = ArtifactsMachine().use { machine ->
        machine.run("add", machine.folder("m2").toString())
        val other = Files.createDirectories(machine.base.resolve("other"))

        val listed = machine.execute(listOf("artifacts", "list", "--workspace", "$other"))

        assertTrue(listed.stdout.startsWith("No artifact folder is registered"), listed.stdout)
    }
}
