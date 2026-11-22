package io.github.krosstack.kscope.service.semantic.process

import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import io.github.krosstack.kscope.service.semantic.WorkerProcess
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WorkerStderrClosureTest {
    @Test
    fun `a stderr closed by the forced destroy keeps the cause already in the pipe`(@TempDir root: Path) {
        val launch = { command: List<String> ->
            val process = ProcessBuilder(command).start()
            StderrClosingProcess(process, UnreadStderrStream(process.errorStream, READER))
        }
        WorkerProcess(command(root, "inference-failed"), launch = launch).use { process ->
            process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
            val reply = process.exchange(EmbeddingRequest.Embed("1", listOf("self-test"), true), 10.seconds)
            val reason = (reply as EmbeddingResponse.Failed).reason
            assertTrue(reason.startsWith("ONNX inference failed\n"))
            assertEquals(1, reason.lines().count { it == CAUSE })
        }
    }

    @Test
    fun `a stderr that stays readable cannot keep close from terminating the worker`(@TempDir root: Path) {
        val launch = { command: List<String> ->
            StderrClosingProcess(ProcessBuilder(command).start(), StalledStderrStream())
        }
        val process = WorkerProcess(command(root, "silent"), launch = launch)
        val started = System.nanoTime()
        process.close()
        assertTrue(System.nanoTime() - started < LIMIT_NANOS)
    }

    private fun command(root: Path, mode: String): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("kscope.semantic.test.classpath"),
        FakeEmbeddingMain::class.java.name,
        root.resolve("pid").toString(),
        mode,
    )

    private companion object {
        const val READER = "semantic-stderr"
        const val CAUSE = "Caused by: IllegalStateException: Missing runtime dependency"
        const val LIMIT_NANOS = 2_500_000_000L
    }
}
