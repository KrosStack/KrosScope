package io.github.krosstack.kscope.cli.serve

import com.github.ajalt.clikt.testing.test
import com.github.ajalt.mordant.rendering.AnsiLevel
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.inScannedDeepWorkspace
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscopeWith
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ServeParityTest {
    @Test
    fun `every query and format keeps local output and exit code`() = inScannedWorkspace { root ->
        val commands = listOf(
            listOf("analyze", "change the library"),
            listOf("context", "change the library", "--estimator", "chars"),
            listOf("symbol", "Lib"),
            listOf("impact", "Lib"),
            listOf("related", "Lib"),
            listOf("graph"),
            listOf("info"),
            listOf("symbol", "MissingName"),
        )
        withTestServer(root) { server ->
            for (command in commands) {
                for (format in listOf("text", "markdown", "json")) {
                    val args = command + listOf("--workspace", root.toString(), "--format", format, "--quiet")
                    val local = runKscopeWith(listOf("--no-server") + args)
                    val remote = runKscopeWith(args)
                    assertEquals(local.stdout, remote.stdout, args.toString())
                    assertEquals(local.statusCode, remote.statusCode, args.toString())
                }
            }
            assertEquals(24, server.requestCount)
        }
    }

    @Test
    fun `usage help version opt outs and structure stay in the client`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val workspace = listOf("--workspace", root.toString())
            assertEquals(2, runKscopeWith(workspace + listOf("graph", "--scope", "neighborhood")).statusCode)
            assertEquals(0, runKscopeWith(workspace + listOf("info", "--help")).statusCode)
            assertEquals(0, runKscopeWith(listOf("--version")).statusCode)
            assertEquals(0, runKscopeWith(workspace + listOf("info", "--no-server")).statusCode)
            val command = KscopeCommand()
            val result = command.testWithVariables(workspace + "info", mapOf("KSCOPE_NO_SERVER" to "1"))
            assertEquals(0, result.statusCode)
            assertEquals(0, runKscopeWith(workspace + listOf("info", "--structure")).statusCode)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `relative workspace and task file use the client directory`() = inScannedWorkspace { root ->
        Files.writeString(root.resolve("task.txt"), "change the library")
        withTestServer(root) {
            val args = listOf(
                "analyze",
                "--workspace",
                root.fileName.toString(),
                "--task-file",
                root.fileName.resolve("task.txt").toString(),
                "--format",
                "json",
                "--quiet",
            )
            val local = runAt(root.parent, listOf("--no-server") + args)
            val remote = runAt(root.parent, args)
            assertEquals(local.stdout, remote.stdout)
            assertEquals(0, remote.statusCode)
        }
    }

    @Test
    fun `client width and color control server rendering`() = inScannedDeepWorkspace { root ->
        withTestServer(root) { server ->
            val args = listOf("symbol", "GreeterHelperFactory", "--workspace", root.toString(), "--quiet")
            val narrow = terminal(args, 60, AnsiLevel.NONE)
            val wide = terminal(args, 120, AnsiLevel.TRUECOLOR)
            val localNarrow = terminal(listOf("--no-server") + args, 60, AnsiLevel.NONE)
            val localWide = terminal(listOf("--no-server") + args, 120, AnsiLevel.TRUECOLOR)
            assertEquals(localNarrow.stdout, narrow.stdout)
            assertEquals(localWide.stdout, wide.stdout)
            assertNotEquals(narrow.stdout, wide.stdout)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `missing index errors keep both streams and the failure exit code`() = inWorkspace { root ->
        withTestServer(root) { server ->
            val args = listOf("symbol", "MissingName", "--workspace", root.toString())
            val local = runKscopeWith(listOf("--no-server") + args)
            val remote = runKscopeWith(args)
            assertEquals(1, local.statusCode)
            assertEquals(local.statusCode, remote.statusCode)
            assertEquals(local.stdout, remote.stdout)
            assertEquals(local.stderr, remote.stderr)
            assertEquals(1, server.requestCount)
        }
    }

    private fun terminal(args: List<String>, width: Int, ansiLevel: AnsiLevel) = KscopeCommand().let { command ->
        command.test(args, width = width, ansiLevel = ansiLevel, outputInteractive = true) {
            command.parseWithExitCodes(it)
        }
    }
}
