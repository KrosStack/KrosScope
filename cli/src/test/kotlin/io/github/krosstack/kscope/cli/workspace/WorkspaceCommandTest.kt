package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceCommandTest {
    @BeforeTest
    fun requireGit() = assumeTrue(gitAvailable())

    private class Machine : AutoCloseable {
        val base: Path = Files.createTempDirectory("kscope-cli-workspace").toRealPath()
        val app: Path = repository(
            "app",
            "settings.gradle.kts" to "rootProject.name = \"app\"\ninclude(\":app\")\n",
            "app/build.gradle.kts" to "",
            "app/src/main/kotlin/org/example/app/Main.kt" to "package org.example.app\n\nfun main() {}\n",
        )
        val library: Path = repository(
            "library",
            "settings.gradle.kts" to "rootProject.name = \"library\"\ninclude(\":model\")\n",
            "model/build.gradle.kts" to "",
            "model/src/main/kotlin/org/example/library/Record.kt" to "package org.example.library\n\nclass Record\n",
        )
        val registry = RepositoryRegistry(base.resolve("home").resolve(RepositoryRegistry.FILE_NAME))

        fun run(vararg arguments: String) =
            WorkspaceCommand { registry }.test(arguments.toList() + listOf("--workspace", app.toString()))

        fun repository(name: String, vararg files: Pair<String, String>): Path {
            val directory = base.resolve(name)
            files.forEach { (path, text) ->
                Files.createDirectories(directory.resolve(path).parent)
                Files.writeString(directory.resolve(path), text)
            }
            git(directory, "init", "-q")
            git(directory, "add", "-A")
            git(directory, "commit", "-q", "-m", "initial")
            return directory
        }

        override fun close() {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `add registers a repository from elsewhere and list shows it`() = Machine().use { machine ->
        val added = machine.run("add", machine.library.toString())

        assertEquals(0, added.statusCode, added.output)
        assertTrue(added.stdout.startsWith("Registered ") && " as library " in added.stdout, added.stdout)
        val listed = machine.run("list")
        assertTrue(listed.stdout.startsWith("1 registered") && "library" in listed.stdout, listed.stdout)
    }

    @Test
    fun `a workspace given to the group is the one that add registers with`() = Machine().use { machine ->
        val added = WorkspaceCommand { machine.registry }.test(
            listOf("--workspace", machine.app.toString(), "add", machine.library.toString()),
        )

        assertEquals(0, added.statusCode, added.output)
        assertTrue(slashed(machine.app) in added.stdout, added.stdout)
        val listed = machine.run("list")
        assertTrue(listed.stdout.startsWith("1 registered") && "library" in listed.stdout, listed.stdout)
    }

    @Test
    fun `a workspace given to the command wins over the one given to the group`() = Machine().use { machine ->
        val other = Files.createDirectories(machine.base.resolve("other"))

        val grouped = listOf("--workspace", other.toString())
        val own = listOf("--workspace", machine.app.toString())

        val arguments = grouped + listOf("add", machine.library.toString()) + own
        val added = WorkspaceCommand { machine.registry }.test(arguments)

        assertEquals(0, added.statusCode, added.output)
        assertTrue("library" in machine.run("list").stdout)
        val elsewhere = WorkspaceCommand { machine.registry }.test(listOf("list", "--workspace", other.toString()))
        assertTrue(elsewhere.stdout.startsWith("No repository is registered"), elsewhere.stdout)
    }

    @Test
    fun `remove unregisters it and a second remove fails`() = Machine().use { machine ->
        machine.run("add", machine.library.toString())

        val removed = machine.run("remove", "library")
        assertEquals(0, removed.statusCode, removed.output)
        assertTrue(removed.stdout.startsWith("Unregistered library"), removed.stdout)
        val again = machine.run("remove", "library")
        assertEquals(1, again.statusCode)
        assertTrue(again.stderr.startsWith("No repository named library"), again.stderr)
    }

    @Test
    fun `a directory that is not a repository is refused with exit code 1`() = Machine().use { machine ->
        val plain = Files.createDirectories(machine.base.resolve("plain"))

        val refused = machine.run("add", plain.toString())

        assertEquals(1, refused.statusCode)
        assertTrue(refused.stderr.contains("is not a Git work tree"), refused.stderr)
        assertTrue(machine.run("list").stdout.startsWith("No repository is registered"))
    }

    @Test
    fun `a scan indexes a registered repository and prints its files under its name`() = Machine().use { machine ->
        val workspace = machine.app.toString()
        try {
            val added = runKscope("workspace", "add", machine.library.toString(), "--workspace", workspace)
            assertEquals(0, added.statusCode, added.output)
            assertEquals(0, runKscope("scan", "--workspace", workspace).statusCode)

            val symbol = runKscope("symbol", "Record", "--workspace", workspace)
            assertTrue("library/model/src/main/kotlin/org/example/library/Record.kt" in symbol.stdout, symbol.stdout)
        } finally {
            runKscope("workspace", "remove", "library", "--workspace", workspace)
        }
    }

    private companion object {
        fun slashed(path: Path): String = path.toString().replace(File.separatorChar, '/')

        fun git(directory: Path, vararg arguments: String) {
            val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile())
                .redirectErrorStream(true).start()
            val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
            check(process.waitFor() == 0) { "git ${arguments.toList()} failed: $output" }
        }

        fun gitAvailable(): Boolean = try {
            val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
            process.inputStream.readAllBytes()
            process.waitFor() == 0
        } catch (_: IOException) {
            false
        }
    }
}
