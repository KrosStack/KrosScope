package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.VerificationResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeWatchFailureCodeTest {
    @Test
    fun `a failure exit code backs off even when the scan runner returns normally`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val attempted = CountDownLatch(1)
        val runner = ScanRunner { workspace, options, _, _ ->
            KrosScope.open(workspace, options).use { it.scan() }.copy(
                verification = VerificationResult(false, 1, emptyList()),
            ).also { attempted.countDown() }
        }
        withTestServer(root, inputs.settings(), runner) { server ->
            inputs.awaitFirstPoll()
            inputs.poll(server, 4, "initial")
            assertTrue(attempted.await(10, TimeUnit.SECONDS))
            val answer = server.query(listOf("info", "--quiet"))
            assertEquals(0, answer.exitCode)
            assertEquals("Index is behind: ${WatchReason.RETRY.text}.\n", answer.stderr)
            assertEquals(WatchReason.RETRY, assertNotNull(server.watchState).reason())
        }
    }
}
