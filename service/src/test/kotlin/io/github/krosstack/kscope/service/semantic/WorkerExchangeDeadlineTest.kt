package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorkerExchangeDeadlineTest {
    @Test
    fun `waiting for the previous exchange counts against the deadline`(@TempDir root: Path) {
        val command = listOf(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("kscope.semantic.test.classpath"),
            FakeEmbeddingMain::class.java.name,
            root.resolve("pid").toString(),
            "silent",
        )
        WorkerProcess(command).use { process ->
            process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), 10.seconds)
            val request = EmbeddingRequest.Embed("1", listOf("text"), false)
            val holder = thread { runCatching { process.exchange(request, 5.seconds) } }
            while (holder.state != Thread.State.TIMED_WAITING) Thread.onSpinWait()
            val started = System.nanoTime()
            val failure = runCatching { process.exchange(request, 300.milliseconds) }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("previous exchange"))
            assertTrue(System.nanoTime() - started < LIMIT_NANOS)
            process.close()
            holder.join()
        }
    }

    private companion object {
        const val LIMIT_NANOS = 3_000_000_000L
    }
}
