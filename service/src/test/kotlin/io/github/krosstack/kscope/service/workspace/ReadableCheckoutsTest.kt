package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.trust.MachineDirectory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadableCheckoutsTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a directory that the files next to an index name is not read`() {
        val base = Files.createTempDirectory("kscope-readable")
        try {
            val workspace = committed(base.resolve("workspace"), "App.kt", "class App")
            val outside = Files.createDirectories(base.resolve("outside"))
            Files.writeString(outside.resolve("App.kt"), "class Outside")
            val environment = environmentIn(base)
            KrosScope.open(workspace, OpenOptions(), environment).use { service ->
                service.scan()
                val repository = service.graph().structure().repositories.single().id.value
                val named = "{\"$repository\":\"${outside.toString().replace("\\", "\\\\")}\"}"
                val index = workspace.resolve(WorkspaceConfig.DEFAULT_INDEX_DIRECTORY)
                Files.writeString(index.resolve("scanned-checkouts.json"), named)

                assertEquals("class App", read(service, "App.kt"))
            }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a workspace moved with its index reads the repositories below it from the new location`() {
        val base = Files.createTempDirectory("kscope-readable")
        try {
            val workspace = Files.createDirectories(base.resolve("workspace"))
            committed(workspace.resolve("app"), "App.kt", "class App")
            committed(workspace.resolve("lib"), "Lib.kt", "class Lib")
            val moved = scanAndMove(workspace, base)

            KrosScope.open(moved, OpenOptions(), environmentIn(base)).use { service ->
                assertEquals("class App", read(service, "App.kt"))
                assertEquals("class Lib", read(service, "Lib.kt"))
            }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a workspace moved with its index reads its own repository from the new location`() {
        val base = Files.createTempDirectory("kscope-readable")
        try {
            val moved = scanAndMove(committed(base.resolve("workspace"), "App.kt", "class App"), base)

            KrosScope.open(moved, OpenOptions(), environmentIn(base)).use { service ->
                assertEquals("class App", read(service, "App.kt"))
            }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    private fun scanAndMove(workspace: Path, base: Path): Path {
        KrosScope.open(workspace, OpenOptions(), environmentIn(base)).use { it.scan() }
        val moved = base.resolve("moved")
        Files.walk(workspace).use { paths ->
            paths.forEach { Files.copy(it, moved.resolve(workspace.relativize(it).toString())) }
        }
        workspace.toFile().deleteRecursively()
        return moved
    }

    private fun read(service: KrosScope, path: String): String {
        val artifact = service.graph().artifacts().map { it.ref }.single { it.path.value == path }
        return service.readWorkspaceFiles().read(artifact).toString(Charsets.UTF_8)
    }

    private fun environmentIn(base: Path) = mapOf(MachineDirectory.VARIABLE to base.resolve("home").toString())::get

    private fun committed(directory: Path, file: String, text: String): Path {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve(file), text)
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
        return directory
    }
}
