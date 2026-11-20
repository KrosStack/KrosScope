package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.OptionalProcessBudget
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticRuntime
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.semantic.WorkerDeadlines
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class SemanticWorkerSessionTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `indexing and queries share one accounted worker`() {
        val fake = FakeSemanticComponents(root)
        SemanticWorkerSession(fake::open).use { session ->
            val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
            ready.provider.embed(listOf("document"))
            assertIs<SemanticWorkerSession.Reply.Embedded>(session.query("query", QueryDeadline()))
            assertEquals(1, fake.starts)
            assertEquals(listOf("query"), fake.queriesEmbedded)
            assertEquals(0, fake.closed)
            session.touch()
        }
        assertEquals(1, fake.closed)
    }

    @Test
    fun `completed indexing leaves one warm query worker`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(root.resolve("scratch"))
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            assertEquals(0, fake.closed)
            assertIs<SemanticQueryOutcome.Applied>(scope.semantic.rank("query"))
            assertEquals(1, fake.starts)
            assertEquals(1, fake.opened)
        }
        assertEquals(1, fake.closed)
    }

    @Test
    fun `idle expiry and release close worker and reservation once`() {
        SemanticQueryFixture(root).use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            var clock = 0L
            var invalidations = 0
            SemanticWorkerSession(fake::open, { clock }) {
                invalidations++
                f.current.invalidate()
            }.use { session ->
                val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
                ready.provider.embed(listOf("document"))
                assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider))
                clock = 20 * 60_000L
                assertFalse(session.expire())
                assertEquals(0, fake.closed)
                session.touch()
                clock += 10 * 60_000L - 1
                assertFalse(session.expire())
                clock++
                assertTrue(session.expire())
                f.assertNoLeases()
                session.invalidate()
                session.close()
                assertEquals(1, fake.closed)
                assertEquals(1, invalidations)
            }
        }
    }

    @Test
    fun `component replacement never reuses the old worker`() {
        val fake = FakeSemanticComponents(root)
        SemanticWorkerSession(fake::open).use { session ->
            assertIs<SemanticWorkerSession.Reply.Embedded>(session.query("first", QueryDeadline()))
            fake.component = "replacement"
            assertIs<SemanticWorkerSession.Reply.Embedded>(session.query("second", QueryDeadline()))
            assertEquals(2, fake.starts)
            assertEquals(1, fake.closed)
        }
        assertEquals(2, fake.closed)
    }

    @Test
    fun `query component replacement closes the old worker with the query deadline`() {
        val fake = FakeSemanticComponents(root)
        SemanticWorkerSession(fake::open).use { session ->
            assertIs<SemanticWorkerSession.Reply.Embedded>(session.query("first", QueryDeadline()))
            fake.component = "replacement"
            val deadline = QueryDeadline(TestTimeSource())
            assertIs<SemanticWorkerSession.Reply.Embedded>(session.query("second", deadline))
            assertSame(deadline, fake.closingDeadlines.single())
            assertEquals(2, fake.starts)
        }
    }

    @Test
    fun `warm query failure closes within its original deadline`() {
        val fake = FakeSemanticComponents(root)
        val clock = TestTimeSource()
        SemanticWorkerSession(fake::open).use { session ->
            val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
            ready.provider.embed(listOf("document"))
            session.touch()
            fake.onQuery = { clock += 10.seconds }
            val deadline = QueryDeadline(clock)
            val result = assertIs<SemanticWorkerSession.Reply.Refused>(session.query("query", deadline))
            assertEquals(WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED, result.result.reason)
            assertSame(deadline, fake.closingDeadlines.single())
            assertEquals(1, fake.closed)
        }
        assertEquals(1, fake.closed)
    }

    @Test
    fun `an OFF query starts nothing`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(root.resolve("scratch"))
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.OFF).use { scope ->
            scope.scan()
            val result = assertIs<SemanticQueryOutcome.Skipped>(scope.semantic.rank("query", MlMode.OFF))
            assertEquals(SemanticQueryOutcome.Reason.OFF, result.reason)
            assertEquals(0, fake.opened)
            assertEquals(0, fake.starts)
            assertTrue(fake.queriesEmbedded.isEmpty())
        }
    }

    @Test
    fun `idle expiry stops the real child and releases its reservation and generation lease`() =
        SemanticQueryFixture(root).use { f ->
            var clock = 0L
            val budget = OptionalProcessBudget(root.resolve("reservations"), allowanceMebibytes = { 1536L })
            SemanticWorkerSession({ realOpening(budget) }, { clock }, f.current::invalidate).use { session ->
                assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider))
                val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
                ready.provider.embed(listOf("document"))
                session.touch()
                assertIs<SemanticWorkerSession.Reply.Embedded>(session.query("query", QueryDeadline()))
                assertEquals(1L, reservations())
                clock = 10 * 60_000L
                assertTrue(session.expire())
                assertEquals(0L, reservations())
                f.assertNoLeases()
                session.close()
                assertEquals(0L, reservations())
            }
            val handle = ProcessHandle.of(Files.readString(root.resolve("pid")).toLong())
            handle.ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
            assertFalse(handle.isPresent && handle.get().isAlive)
        }

    @Nested
    inner class Lifecycle {
        @Test
        fun `warm lost reservation expires before the idle deadline but not during indexing`() {
            val fake = FakeSemanticComponents(root)
            SemanticWorkerSession(fake::open, { 0L }).use { session ->
                val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
                ready.provider.embed(listOf("document"))
                fake.lost = "Reservation released"
                assertFalse(session.expire())
                assertEquals(0, fake.closed)
                session.touch()
                assertTrue(session.expire())
                assertEquals(1, fake.closed)
            }
            assertEquals(1, fake.closed)
        }

        @Test
        fun `OFF maintenance reports idle cleanup failure without throwing`() = SemanticQueryFixture(root).use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            var clock = 0L
            SemanticRuntime(f.directory, fake::open, fake::unavailable, f.workspace) { clock }.use { runtime ->
                assertIs<SemanticQueryOutcome.Applied>(runtime.rank("query"))
                fake.closeFailure = IOException("Reservation locked")
                clock = 10 * 60_000L
                assertFalse(runtime.advance(SemanticIndexingMode.OFF))
                assertEquals(SemanticState.WORKER_UNAVAILABLE, runtime.status.state)
                assertEquals(
                    "Semantic indexing failed: Reservation locked. The previous semantic index stays in use.",
                    runtime.notice,
                )
                assertEquals(1, fake.closed)
                f.assertNoLeases()
            }
        }

        @Test
        fun `revision maintenance reports cleanup failure without throwing`() = SemanticQueryFixture(root).use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            SemanticRuntime(f.directory, fake::open, fake::unavailable, f.workspace).use { runtime ->
                runtime.finish()
                assertEquals(SemanticState.USED, runtime.status.state)
                assertIs<SemanticQueryOutcome.Applied>(runtime.rank("query"))
                fake.closeFailure = IOException("Reservation locked")
                f.revision++
                assertFalse(runtime.advance())
                assertEquals(SemanticState.WORKER_UNAVAILABLE, runtime.status.state)
                assertEquals(
                    "Semantic indexing failed: Reservation locked. The previous semantic index stays in use.",
                    runtime.notice,
                )
                assertEquals(1, fake.closed)
                f.assertNoLeases()
            }
        }

        @Test
        fun `an unreadable replacement identity ends the indexing borrow`() {
            SemanticTestWorkspace.create(root)
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
                scope.scan()
                while (fake.starts == 0) assertTrue(scope.semantic.advance())
                fake.fingerprintFailure = IOException("Replacement unreadable")
                assertTrue(scope.semantic.advance())
                assertEquals(1, fake.closed)
            }
        }

        @Test
        fun `warm cleanup failure preserves the worker refusal`() {
            val fake = FakeSemanticComponents(root)
            SemanticWorkerSession(fake::open).use { session ->
                val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
                ready.provider.embed(listOf("document"))
                session.touch()
                fake.queryRefusal = WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT
                fake.closeFailure = IOException("Reservation locked")
                val reply = assertIs<SemanticWorkerSession.Reply.Refused>(session.query("query", QueryDeadline()))
                assertEquals(WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT, reply.result.reason)
                assertTrue(reply.result.detail.contains("Reservation locked"))
                assertEquals(1, fake.closed)
            }
        }

        @Test
        fun `runtime expiry releases its retained query snapshot`() = SemanticQueryFixture(root).use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            var clock = 0L
            SemanticRuntime(f.directory, fake::open, fake::unavailable, f.workspace) { clock }.use { runtime ->
                assertIs<SemanticQueryOutcome.Applied>(runtime.rank("query"))
                clock = 10 * 60_000L
                runtime.advance(SemanticIndexingMode.OFF)
                assertEquals(1, fake.closed)
                f.assertNoLeases()
            }
            assertEquals(1, fake.closed)
        }

        @Test
        fun `query failure leaves an active indexing borrow usable`() {
            val fake = FakeSemanticComponents(root)
            SemanticWorkerSession(fake::open).use { session ->
                val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
                ready.provider.embed(listOf("document"))
                fake.queryRefusal = WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT
                assertIs<SemanticWorkerSession.Reply.Refused>(session.query("query", QueryDeadline()))
                assertEquals(0, fake.closed)
                assertEquals("doc", ready.provider.cap("doc", 3))
                assertEquals(1, fake.starts)
                session.touch()
            }
            assertEquals(1, fake.closed)
        }
    }

    private fun realOpening(budget: OptionalProcessBudget): SemanticComponents.Opening.Ready {
        val identity = ModelIdentity("fake", "test", 2, "int8-dynamic", 1)
        return SemanticComponents.Opening.Ready(
            LazyWorkerProvider(identity, "fake") { deadline, diagnostics ->
                val reservation = assertIs<OptionalProcessBudget.Reservation.Granted>(budget.tryReserve()).reservation
                val worker = WorkerEmbeddingProvider(
                    identity,
                    command(),
                    WorkerDeadlines(absoluteQuery = deadline),
                    diagnostics = diagnostics,
                )
                LazyWorkerProvider.Started(worker, reservation::lost, worker::embedQuery) { closing ->
                    try {
                        worker.close(closing)
                    } finally {
                        reservation.close()
                    }
                }
            },
        )
    }

    private fun reservations(): Long = Files.list(root.resolve("reservations")).use { files ->
        files.filter { it.fileName.toString().endsWith(".reservation") }.count()
    }

    private fun command(): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("kscope.semantic.test.classpath"),
        FakeEmbeddingMain::class.java.name,
        root.resolve("pid").toString(),
        "normal",
    )
}
