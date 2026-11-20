package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.InstalledComponent
import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.OptionalProcessBudget
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.WorkerDeadlines
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class QueryWorkerDeadlineTest {
    @Test
    fun `expired work cannot start another exchange`(@TempDir root: Path) {
        val clock = TestTimeSource()
        val deadline = QueryDeadline(clock)
        WorkerEmbeddingProvider(identity, command(root), WorkerDeadlines()).use { provider ->
            clock += 10.seconds
            val result = provider.embedQuery("text", deadline) {}
            assertRefusal(WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED, result)
            assertContentEquals(floatArrayOf(1f, 4f), provider.embedQuery("text"))
        }
        assertStopped(root)
    }

    @Test
    fun `cold handshake receives only the remaining query budget`(@TempDir root: Path) {
        val existing = ProcessHandle.current().children().use { it.map(ProcessHandle::pid).toList().toSet() }
        val children = mutableListOf<ProcessHandle>()
        val clock = AdvancingClock(2) {
            ProcessHandle.current().children().use { processes ->
                children.addAll(processes.filter { it.pid() !in existing }.toList())
            }
        }
        val deadline = QueryDeadline(clock)
        assertFailsWith<IOException> {
            WorkerEmbeddingProvider(
                identity,
                command(root),
                WorkerDeadlines(absoluteQuery = deadline),
                diagnostics = {},
            ).close()
        }
        assertTrue(children.isNotEmpty())
        children.forEach { child ->
            child.onExit().get(5, TimeUnit.SECONDS)
            assertFalse(child.isAlive)
        }
    }

    @Test
    fun `worker-lock wait consumes the query allowance`(@TempDir root: Path) {
        val clock = TestTimeSource()
        val entered = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val provider = WorkerEmbeddingProvider(identity, command(root, "stderr-forever"), diagnostics = {
            if (it.startsWith("at noisy")) entered.countDown()
        })
        val lazy = LazyWorkerProvider(identity, "fake") { _, _ ->
            LazyWorkerProvider.Started(provider, { null }, provider::embedQuery, provider::close)
        }
        try {
            val indexing = pool.submit { runCatching { lazy.embed(listOf("text")) } }
            check(entered.await(10, TimeUnit.SECONDS))
            val deadline = QueryDeadline(clock)
            clock += 10.seconds - 1.nanoseconds
            val query = pool.submit<WorkerEmbeddingProvider.QueryResult> { lazy.embedQuery("text", deadline) {} }
            assertRefusal(WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT, query.get(5, TimeUnit.SECONDS))
            assertFalse(indexing.isDone)
        } finally {
            provider.close()
            lazy.close()
            pool.shutdownNow()
            check(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertStopped(root)
    }

    @Test
    fun `busy memory admission refuses without launching`(@TempDir root: Path) {
        val components = installed(root)
        val budgetDirectory = root.resolve("reservations")
        Files.createDirectories(budgetDirectory)
        FileChannel.open(
            budgetDirectory.resolve(".lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use {
                assertIs<SemanticComponents.Opening.Ready>(components.open()).useProvider { lazy ->
                    assertRefusal(
                        WorkerEmbeddingProvider.Refusal.MEMORY_BUSY,
                        lazy.embedQuery("text", QueryDeadline(TestTimeSource())) {},
                    )
                }
                assertEquals(0L, reservations(root))
            }
        }
    }

    @Test
    fun `failed startup releases its reservation`(@TempDir root: Path) {
        val components = installed(root)
        assertIs<SemanticComponents.Opening.Ready>(components.open()).useProvider { lazy ->
            Files.delete(root.resolve("semantic/1/lib/placeholder"))
            Files.delete(root.resolve("semantic/1/lib"))
            assertRefusal(
                WorkerEmbeddingProvider.Refusal.STARTUP_FAILED,
                lazy.embedQuery("text", QueryDeadline(TestTimeSource())) {},
            )
            assertEquals(0L, reservations(root))
        }
        assertEquals(0L, reservations(root))
    }

    @Test
    fun `expired reply is discarded and only its owned child is closed`(@TempDir root: Path) {
        val clock = TestTimeSource()
        val closed = AtomicInteger()
        val provider = WorkerEmbeddingProvider(identity, command(root))
        val lazy = LazyWorkerProvider(identity, "fake") { _, _ ->
            LazyWorkerProvider.Started(provider, { null }, { text, deadline, owned, diagnostics ->
                val result = provider.embedQuery(text, deadline, owned, diagnostics)
                clock += 10.seconds
                result
            }) {
                provider.close()
                closed.incrementAndGet()
            }
        }
        lazy.use {
            assertRefusal(
                WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED,
                lazy.embedQuery("text", QueryDeadline(clock)) {},
            )
            assertEquals(1, closed.get())
        }
        assertEquals(1, closed.get())
        assertStopped(root)
    }

    @Test
    fun `expired borrowed reply leaves indexing child usable`(@TempDir root: Path) {
        val clock = TestTimeSource()
        WorkerEmbeddingProvider(identity, command(root)).use { provider ->
            val lazy = LazyWorkerProvider(identity, "fake") { _, _ ->
                LazyWorkerProvider.Started(provider, { null }, { text, deadline, owned, diagnostics ->
                    assertFalse(owned)
                    val result = provider.embedQuery(text, deadline, owned, diagnostics)
                    clock += 10.seconds
                    result
                }) { provider.close() }
            }
            lazy.use {
                assertEquals("te", lazy.cap("text", 2))
                assertRefusal(
                    WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED,
                    lazy.embedQuery("text", QueryDeadline(clock)) {},
                )
                assertEquals("text", lazy.cap("text", 4))
            }
        }
        assertStopped(root)
    }

    @Test
    fun `query diagnostics do not alter fallback output`(@TempDir root: Path) {
        val diagnostics = mutableListOf<String>()
        val plain = fallback(Files.createDirectory(root.resolve("plain")), "normal", diagnostics)
        val noisy = fallback(Files.createDirectory(root.resolve("noisy")), "inference-failed", diagnostics)
        assertContentEquals(plain, noisy)
        assertTrue(diagnostics.any { it.contains("Missing runtime dependency") })
    }

    private class AdvancingClock(private val expireOnRead: Int, private val onExpiry: () -> Unit = {}) : TimeSource {
        private val clock = TestTimeSource()
        private val reads = AtomicInteger()

        override fun markNow(): TimeMark = object : TimeMark {
            private val mark = clock.markNow()

            override fun elapsedNow(): Duration {
                if (reads.incrementAndGet() == expireOnRead) {
                    onExpiry()
                    clock += 10.seconds
                }
                return mark.elapsedNow()
            }
        }
    }

    @Nested
    inner class Admission {
        @Test
        fun `insufficient memory differs from admission contention`(@TempDir root: Path) {
            val budget = OptionalProcessBudget(root, allowanceMebibytes = { 1536L })
            val first = assertIs<OptionalProcessBudget.Reservation.Granted>(budget.tryReserve())
            try {
                val refused = assertIs<OptionalProcessBudget.Reservation.Refused>(budget.tryReserve())
                assertEquals(OptionalProcessBudget.Refusal.INSUFFICIENT_MEMORY, refused.refusal)
                FileChannel.open(root.resolve(".lock"), StandardOpenOption.WRITE).use { channel ->
                    channel.lock().use {
                        val busy = assertIs<OptionalProcessBudget.Reservation.Refused>(budget.tryReserve())
                        assertEquals(OptionalProcessBudget.Refusal.BUSY, busy.refusal)
                    }
                }
            } finally {
                first.reservation.close()
            }
        }

        @Test
        fun `simultaneous query reservations cannot overcommit`(@TempDir root: Path) {
            val budget = OptionalProcessBudget(root, allowanceMebibytes = { 1536L })
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(4)
            try {
                val attempts = List(4) {
                    pool.submit<OptionalProcessBudget.Reservation> {
                        start.await()
                        budget.tryReserve()
                    }
                }
                start.countDown()
                val granted = attempts.map { it.get(10, TimeUnit.SECONDS) }
                    .filterIsInstance<OptionalProcessBudget.Reservation.Granted>()
                try {
                    assertEquals(1, granted.size)
                } finally {
                    granted.forEach { it.reservation.close() }
                }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Nested
    inner class Exchanges {
        @Test
        fun `worker exchange failure has its own refusal`(@TempDir root: Path) {
            WorkerEmbeddingProvider(identity, command(root, "bad")).use { provider ->
                assertRefusal(
                    WorkerEmbeddingProvider.Refusal.WORKER_FAILED,
                    provider.embedQuery("text", QueryDeadline(TestTimeSource())) {},
                )
            }
            assertStopped(root)
        }

        @Test
        fun `late process reply is discarded and its owned process stops`(@TempDir root: Path) {
            WorkerEmbeddingProvider(identity, command(root)).use { provider ->
                assertRefusal(
                    WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT,
                    provider.embedQuery("text", QueryDeadline(AdvancingClock(6)), true) {},
                )
                assertStopped(root)
            }
        }

        @Test
        fun `late process reply cannot close an indexing process`(@TempDir root: Path) {
            WorkerEmbeddingProvider(identity, command(root)).use { provider ->
                assertRefusal(
                    WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT,
                    provider.embedQuery("text", QueryDeadline(AdvancingClock(6)), false) {},
                )
                assertEquals("text", provider.cap("text", 4))
            }
            assertStopped(root)
        }

        @Test
        fun `silent exchange times out within the remainder`(@TempDir root: Path) {
            WorkerEmbeddingProvider(identity, command(root, "silent")).use { provider ->
                val clock = TestTimeSource()
                val deadline = QueryDeadline(clock)
                clock += 10.seconds - 1.nanoseconds
                assertRefusal(
                    WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT,
                    provider.embedQuery("text", deadline, true) {},
                )
                assertStopped(root)
            }
        }
    }

    private companion object {
        val identity = ModelIdentity("fake", "test", 2, "int8-dynamic", 1)

        private fun fallback(root: Path, mode: String, diagnostics: MutableList<String>): ByteArray {
            val bytes = ByteArrayOutputStream()
            val originalOut = System.out
            val originalErr = System.err
            PrintStream(bytes).use { output ->
                System.setOut(output)
                System.setErr(output)
                try {
                    WorkerEmbeddingProvider(identity, command(root, mode)).use { provider ->
                        provider.embedQuery("text", QueryDeadline(TestTimeSource()), diagnostics = diagnostics::add)
                    }
                    output.print("structural fallback\n")
                } finally {
                    System.setOut(originalOut)
                    System.setErr(originalErr)
                }
            }
            assertStopped(root)
            return bytes.toByteArray()
        }

        private fun installed(root: Path, allowance: () -> Long = { 3000L }): SemanticComponents {
            val worker = Files.createDirectories(root.resolve("semantic/1/lib"))
            Files.writeString(worker.resolve("placeholder"), "")
            InstalledComponent.write(InstalledComponent("semantic", "1", worker.parent, listOf("lib/placeholder")))
            val model = Files.createDirectories(root.resolve("semantic-model/1/model"))
            Files.writeString(
                model.resolve("model.json"),
                """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
            )
            InstalledComponent.write(
                InstalledComponent("semantic-model", "1", model.parent, listOf("model/model.json")),
            )
            return SemanticComponents(
                ComponentDirectory(root),
                OptionalProcessBudget(root.resolve("reservations"), allowanceMebibytes = allowance),
                { emptyMap() },
            )
        }

        private fun SemanticComponents.Opening.Ready.useProvider(action: (LazyWorkerProvider) -> Unit) {
            try {
                action(provider)
            } finally {
                close()
            }
        }

        private fun reservations(root: Path): Long = Files.list(root.resolve("reservations")).use { files ->
            files.filter { it.toString().endsWith(".reservation") }.count()
        }

        private fun assertRefusal(
            expected: WorkerEmbeddingProvider.Refusal,
            result: WorkerEmbeddingProvider.QueryResult,
        ) {
            assertEquals(expected, assertIs<WorkerEmbeddingProvider.QueryResult.Refused>(result).reason)
        }

        private fun assertStopped(root: Path) {
            val handle = ProcessHandle.of(Files.readString(root.resolve("pid")).toLong())
            handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
            assertTrue(handle.isEmpty || !handle.get().isAlive)
        }

        private fun command(root: Path, mode: String = "normal"): List<String> = listOf(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("kscope.semantic.test.classpath"),
            FakeEmbeddingMain::class.java.name,
            root.resolve("pid").toString(),
            mode,
        )
    }
}
