package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.RecordingEmbedding
import io.github.krosstack.kscope.service.semantic.WorkerDeadlines
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class QueryWorkerCleanupTest {
    private val identity = ModelIdentity("fake", "test", 2, "int8-dynamic", 1)

    @Test
    fun `expired work closes a worker the query owns`(@TempDir root: Path) {
        val clock = TestTimeSource()
        val deadline = QueryDeadline(clock)
        WorkerEmbeddingProvider(identity, command(root), WorkerDeadlines()).use { provider ->
            clock += 10.seconds
            val result = provider.embedQuery("text", deadline, ownsProcess = true) {}
            assertEquals(WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED, refused(result).reason)
            val handle = ProcessHandle.of(Files.readString(root.resolve("pid")).toLong())
            handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
            assertTrue(handle.isEmpty || !handle.get().isAlive)
        }
    }

    @Test
    fun `a failed cleanup keeps the typed refusal`() {
        val refusal = WorkerEmbeddingProvider.QueryResult.Refused(WorkerEmbeddingProvider.Refusal.WORKER_FAILED, "x")
        val lazy = LazyWorkerProvider(identity, "fake") { _, _ ->
            LazyWorkerProvider.Started(RecordingEmbedding(), { null }, { _, _, _, _ -> refusal }) {
                throw IOException("reservation is locked")
            }
        }
        lazy.use {
            val result = refused(it.embedQuery("text", QueryDeadline(TestTimeSource())) {})
            assertEquals(WorkerEmbeddingProvider.Refusal.WORKER_FAILED, result.reason)
            assertTrue(result.detail.endsWith("reservation is locked"))
        }
    }

    private fun refused(result: WorkerEmbeddingProvider.QueryResult) =
        assertIs<WorkerEmbeddingProvider.QueryResult.Refused>(result)

    private fun command(root: Path): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("kscope.semantic.test.classpath"),
        FakeEmbeddingMain::class.java.name,
        root.resolve("pid").toString(),
        "normal",
    )
}
