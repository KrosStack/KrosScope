package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeFreshnessTest {
    @Test
    fun `external and forwarded scans invalidate the warm index`() = inScannedWorkspace { root ->
        withTestServer(root) { _ ->
            val args = listOf("symbol", "AddedClass", "--workspace", root.toString(), "--format", "json")
            runKscopeWith(args)
            writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nclass AddedClass\n")
            KrosScope.open(root).use { it.scan() }
            assertTrue(runKscopeWith(args).stdout.contains("AddedClass"))
            assertEquals(runKscopeWith(listOf("--no-server") + args).stdout, runKscopeWith(args).stdout)
            writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nclass NextClass\n")
            assertEquals(0, runKscope("scan", "--workspace", root.toString(), "--quiet").statusCode)
            val next = runKscope("symbol", "NextClass", "--workspace", root.toString(), "--format", "json")
            assertTrue(next.stdout.contains("NextClass"))
            val local = runKscope(
                "symbol",
                "NextClass",
                "--workspace",
                root.toString(),
                "--format",
                "json",
                "--no-server",
            )
            assertEquals(local.stdout, next.stdout)
            assertEquals(0, runKscope("scan", "--rebuild", "--verify", "--workspace", root.toString()).statusCode)
            val refused = runKscope("scan", "--no-server", "--workspace", root.toString())
            assertEquals(1, refused.statusCode)
            assertTrue(refused.stderr.contains("stopped first"))
        }
    }

    @Test
    fun `trusted scans pass trust to the scan service without executing Gradle`() = inScannedWorkspace { root ->
        var scannedWith: OpenOptions? = null
        val scan = ScanRunner { workspace, options, rebuild, verify ->
            scannedWith = options
            KrosScope.open(workspace, options.copy(trustBuild = false)).use { it.scan(rebuild, verify) }
        }
        withTestServer(root, scanRunner = scan) { server ->
            val result = runKscope("scan", "--trust-build", "--workspace", root.toString())
            assertEquals(0, result.statusCode)
            assertTrue(assertNotNull(scannedWith).trustBuild)
            assertEquals(1, server.requestCount)
            assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
        }
    }

    @Test
    fun `another version falls back for queries and refuses scans`() = inScannedWorkspace { root ->
        withTestServer(root) {
            val metadata = assertNotNull(ServeMetadata.read(root))
            metadata.copy(toolVersion = "another").write(root)
            val args = listOf("info", "--workspace", root.toString())
            val remote = runKscopeWith(args)
            assertEquals(runKscopeWith(listOf("--no-server") + args).stdout, remote.stdout)
            assertTrue(remote.stderr.contains("another version"))
            assertEquals(1, runKscope("scan", "--workspace", root.toString()).statusCode)
        }
    }

    @Test
    fun `concurrent requests complete without mixed output`() = inScannedWorkspace { root ->
        val info = runKscope("info", "--workspace", root.toString(), "--format", "json").stdout
        val graph = runKscope("graph", "--workspace", root.toString(), "--format", "json").stdout
        withTestServer(root) { server ->
            val executor = Executors.newFixedThreadPool(2)
            try {
                val first = executor.submit<String> { server.query(listOf("info", "--format", "json")).stdout }
                val second = executor.submit<String> { server.query(listOf("graph", "--format", "json")).stdout }
                assertEquals(info, first.get(30, TimeUnit.SECONDS))
                assertEquals(graph, second.get(30, TimeUnit.SECONDS))
                assertEquals(2, server.requestCount)
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
