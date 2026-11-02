package io.github.krosstack.kscope.cli.serve

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.ClientRendering
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.RecordedEnvironment
import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ScanStages
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeScanTest {
    @Test
    fun `scan rebuild and verify keep local stdout and exit code`() = inWorkspace { root ->
        val scan = ScanRunner { workspace, options, rebuild, verify ->
            KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }.copy(stageMillis = emptyMap())
        }
        val args = listOf("scan", "--rebuild", "--verify", "--progress", "never", "--format", "json")
        val command = KscopeCommand(execution = CommandExecution(root, scanRunner = scan))
        val local = command.test(args) { command.parseWithExitCodes(it) }
        withTestServer(root, scanRunner = scan) { server ->
            for (name in listOf("scan", "update")) {
                val remote = server.query(listOf(name) + args.drop(1))
                assertEquals(0, local.statusCode)
                assertEquals(local.statusCode, remote.exitCode)
                assertEquals(local.stdout, remote.stdout)
                assertEquals(local.stderr, remote.stderr)
            }
        }
    }

    @Test
    fun `a failed or throwing scan reopens the warm service and keeps serving`() = inScannedWorkspace { root ->
        for (failure in listOf(IOException("scan failed"), IllegalStateException("scan threw"))) {
            val scan = ScanRunner { _, _, _, _ -> throw failure }
            withTestServer(root, scanRunner = scan) { server ->
                val before = server.query(listOf("info", "--format", "json"))
                val result = server.query(listOf("scan"))
                assertEquals(1, result.exitCode)
                assertTrue(result.stderr.contains(failure.message.orEmpty()))
                assertEquals(before, server.query(listOf("info", "--format", "json")))
            }
        }
    }

    @Test
    fun `scan progress reaches the client before the scan finishes`() = inScannedWorkspace { root ->
        val entered = CountDownLatch(1)
        val progress = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scan = ScanRunner { workspace, options, rebuild, verify ->
            options.progress.onEvent(ProgressEvent.Started(ScanStages.DISCOVERY))
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }
        }
        withTestServer(root, scanRunner = scan) { server ->
            assertStreamingProgress(server, entered, progress, release)
        }
    }

    @Test
    fun `forwarded scans report progress to a console and honor never`() = inScannedWorkspace { root ->
        withTestServer(root) { _ ->
            writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nclass Changed\n")
            val console = RecordedEnvironment(stderrIsConsole = true)
            val always = runKscope(
                console.environment,
                "scan",
                "--workspace",
                root.toString(),
                "--progress",
                "always",
            )
            assertEquals(0, always.statusCode)
            assertTrue(always.stderr.contains("Listing files"), always.stderr)
            writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nclass ChangedAgain\n")
            val never = runKscope(
                console.environment,
                "scan",
                "--workspace",
                root.toString(),
                "--progress",
                "never",
            )
            assertEquals(0, never.statusCode)
            assertEquals("", never.stderr)
        }
    }

    private fun assertStreamingProgress(
        server: ServeServer,
        entered: CountDownLatch,
        progress: CountDownLatch,
        release: CountDownLatch,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val request = ServeRequest(
                server.metadata.token,
                listOf("scan", "--progress", "always"),
                server.workspace.toString(),
                ClientRendering(stderrIsConsole = true),
            )
            val running = executor.submit<Int?> {
                ServeClient.exchange(server.metadata, request) {
                    if (it.stream == "stderr" && it.text.contains("Listing files")) progress.countDown()
                }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(progress.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(0, running.get(10, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
