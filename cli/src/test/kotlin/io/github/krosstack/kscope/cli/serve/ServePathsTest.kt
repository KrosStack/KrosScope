package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscopeWith
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ServePathsTest {
    @Test
    fun `workspace path spelling is preserved in index locations`() = inScannedWorkspace { root ->
        val spelling = Path.of(root.toString().uppercase(Locale.ROOT))
        if (Files.isDirectory(spelling)) {
            val args = listOf("info", "--workspace", spelling.toString(), "--format", "json")
            val local = runKscopeWith(listOf("--no-server") + args)
            withTestServer(root) {
                val remote = runKscopeWith(args)
                assertEquals(0, remote.statusCode)
                assertEquals(local.stdout, remote.stdout)
            }
        }
    }

    @Test
    fun `argument files task files and default workspace use the client directory`() = inScannedWorkspace { root ->
        Files.writeString(root.resolve("task.txt"), "change the library")
        Files.writeString(root.resolve("args.txt"), "context --task-file task.txt --estimator chars --format json")
        val local = runAt(root, listOf("--no-server", "@args.txt"))
        withTestServer(root) { server ->
            val remote = runAt(root, listOf("@args.txt"))
            assertEquals(0, remote.statusCode)
            assertEquals(local.stdout, remote.stdout)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `workspace relative nodes and task files agree from another directory`() = inScannedWorkspace { root ->
        Files.writeString(root.resolve("task.txt"), "change the library")
        withTestServer(root) { server ->
            val commands = listOf(
                listOf("context", "--task-file", root.fileName.resolve("task.txt").toString(), "--estimator", "chars"),
                listOf("analyze", "lib/src/main/kotlin/Lib.kt"),
                listOf("symbol", "lib/src/main/kotlin/Lib.kt:1"),
                listOf("impact", "lib/src/main/kotlin/Lib.kt"),
                listOf("related", "lib/src/main/kotlin/Lib.kt"),
                listOf("graph", "--scope", "neighborhood", "--node", "lib/src/main/kotlin/Lib.kt"),
            )
            for (command in commands) {
                val args = command + listOf("--workspace", root.fileName.toString(), "--format", "json", "--quiet")
                val local = runAt(root.parent, listOf("--no-server") + args)
                val remote = runAt(root.parent, args)
                assertEquals(local.stdout, remote.stdout)
                assertEquals(local.statusCode, remote.statusCode)
            }
            assertEquals(6, server.requestCount)
        }
    }
}
