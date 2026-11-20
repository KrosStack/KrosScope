package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.semantic.search.SemanticFileRanking
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.service.semantic.advanceSemanticIndex
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class SemanticQueryTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `usable cold query starts only after snapshot admission`() = fixture { f, fake, query ->
        fake.onQuery = {
            assertEquals(1, f.generationOpens)
            assertTrue(Files.list(f.directory.resolve("embeddings-leases")).use { it.findAny().isPresent })
        }
        val result = assertIs<SemanticQueryOutcome.Applied>(query.rank("query"))
        val snapshot = assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider)).snapshot
        assertEquals(SemanticFileRanking.rank(snapshot.entries, f.provider.embedQuery("query")), result.files)
        assertEquals(snapshot.identity, result.snapshotIdentity)
        assertEquals(SemanticState.USED, result.status.state)
        assertEquals(1, fake.starts)
        assertTrue(fake.provider.embedded.isEmpty())
        assertEquals(0, fake.provider.capCalls)
    }

    @Test
    fun `component replacement after admission supersedes the query`() = fixture { f, fake, query ->
        fake.onFingerprint = {
            if (f.generationOpens == 1) fake.component = "replacement"
        }
        assertSkipped(query, SemanticQueryOutcome.Reason.SUPERSEDED)
        assertEquals(2, fake.opened)
        assertEquals(listOf("query"), fake.queriesEmbedded)
        f.assertNoLeases()
    }

    @Test
    fun `behind queries use current published vectors only`() = fixture { f, _, query ->
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        val kept = generation.chunks.first()
        val obsolete = kept.copy(
            chunk = kept.chunk.copy(
                key = "f".repeat(64),
                artifact = kept.chunk.artifact.copy(path = RepoPath("removed.kt")),
            ),
        )
        f.store.publish(GenerationFile(generation.header.copy(chunkCount = 2), listOf(kept, obsolete)))
        val result = assertIs<SemanticQueryOutcome.Applied>(query.rank("query"))
        val keys = f.inventory.flatMap { it.chunks }.map { it.key }.toSet()
        assertEquals(keys.size - 1, result.behindChunks)
        assertEquals(result.behindChunks, result.status.behindChunks)
        assertEquals(SemanticState.BEHIND, result.status.state)
        assertTrue(result.files.none { it.path.value == "removed.kt" })
        assertTrue(result.files.isNotEmpty())
    }

    @Test
    fun `missing unproven and incompatible snapshots start no process`() = fixture { f, fake, query ->
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        Files.delete(f.directory.resolve("embeddings.json"))
        assertSkipped(query, SemanticQueryOutcome.Reason.UNAVAILABLE_GENERATION)
        f.store.publish(generation)
        f.cache.write(f.provider.identity, f.inventory.drop(1))
        assertSkipped(query, SemanticQueryOutcome.Reason.UNPROVEN_INVENTORY)
        f.cache.write(f.provider.identity, f.inventory)
        f.store.publish(
            GenerationFile(
                generation.header.copy(identity = f.provider.identity.copy(revision = "other")),
                generation.chunks,
            ),
        )
        assertSkipped(query, SemanticQueryOutcome.Reason.UNAVAILABLE_GENERATION)
        assertEquals(0, fake.starts)
        assertTrue(fake.queriesEmbedded.isEmpty())
        f.assertNoLeases()
    }

    @Test
    fun `graph or manifest changes discard a completed vector`() = fixture { f, fake, query ->
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        val changes = listOf<() -> Unit>(
            { f.revision++ },
            { f.store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1))) },
            { f.component = "replacement" },
        )
        for (change in changes) {
            fake.onQuery = change
            assertSkipped(query, SemanticQueryOutcome.Reason.SUPERSEDED)
            f.assertNoLeases()
        }
    }

    @Test
    fun `indexing OFF does not override an ON query`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(root.resolve("scratch"))
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { it.scan() }
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.OFF).use { scope ->
            assertIs<SemanticQueryOutcome.Applied>(scope.semantic.rank("query"))
            assertEquals(listOf("query"), fake.queriesEmbedded)
        }
    }

    @Test
    fun `ML OFF does not change background indexing policy`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(root.resolve("scratch"))
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            val off = assertIs<SemanticQueryOutcome.Skipped>(scope.semantic.rank("query", MlMode.OFF))
            assertEquals(SemanticQueryOutcome.Reason.OFF, off.reason)
            scope.advanceSemanticIndex()
            scope.semantic.finish()
            assertEquals(1, fake.starts)
            assertTrue(fake.provider.embedded.isNotEmpty())
            assertTrue(fake.queriesEmbedded.isEmpty())
        }
    }

    @Test
    fun `worker refusals preserve every typed reason and release the query lease`() = fixture { f, fake, query ->
        val reasons = mapOf(
            WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED to SemanticQueryOutcome.Reason.DEADLINE_EXPIRED,
            WorkerEmbeddingProvider.Refusal.MEMORY_BUSY to SemanticQueryOutcome.Reason.MEMORY_BUSY,
            WorkerEmbeddingProvider.Refusal.STARTUP_FAILED to SemanticQueryOutcome.Reason.STARTUP_FAILED,
            WorkerEmbeddingProvider.Refusal.WORKER_FAILED to SemanticQueryOutcome.Reason.WORKER_FAILED,
            WorkerEmbeddingProvider.Refusal.WORKER_TIMED_OUT to SemanticQueryOutcome.Reason.WORKER_TIMED_OUT,
        )
        for ((refusal, reason) in reasons) {
            fake.queryRefusal = refusal
            assertSkipped(query, reason)
            f.assertNoLeases()
        }
    }

    @Test
    fun `a completed query past its deadline is discarded`() = SemanticQueryFixture(root).use { f ->
        val fake = FakeSemanticComponents(root.resolve("scratch"))
        val clock = TestTimeSource()
        SemanticWorkerSession(fake::open).use { session ->
            SemanticQuery(f.current, session) { QueryDeadline(clock) }.use { query ->
                fake.onQuery = { clock += 10.seconds }
                assertSkipped(query, SemanticQueryOutcome.Reason.DEADLINE_EXPIRED)
                f.assertNoLeases()
            }
        }
    }

    @Nested
    inner class Admission {
        @Test
        fun `unreadable component metadata releases a warm worker with the query deadline`() =
            SemanticQueryFixture(root).use { f ->
                val fake = FakeSemanticComponents(root.resolve("scratch"))
                val deadline = QueryDeadline(TestTimeSource())
                SemanticWorkerSession(fake::open).use { session ->
                    val ready = assertIs<SemanticComponents.Opening.Ready>(session.acquire())
                    ready.provider.embed(listOf("document"))
                    session.touch()
                    fake.fingerprintFailure = IOException("Component metadata unreadable")
                    SemanticQuery(f.current, session) { deadline }.use { query ->
                        val result = assertIs<SemanticQueryOutcome.Skipped>(query.rank("query"))
                        assertEquals(SemanticQueryOutcome.Reason.STARTUP_FAILED, result.reason)
                        assertTrue(result.detail.contains("Component metadata unreadable"))
                        assertEquals(1, fake.closed)
                        assertSame(deadline, fake.closingDeadlines.single())
                        f.assertNoLeases()
                    }
                }
            }

        @Test
        fun `absent and disabled components retain their own statuses`() = fixture { _, fake, query ->
            val reasons = mapOf(
                SemanticState.NOT_INSTALLED to SemanticQueryOutcome.Reason.NOT_INSTALLED,
                SemanticState.DISABLED to SemanticQueryOutcome.Reason.DISABLED,
            )
            for ((state, reason) in reasons) {
                fake.problem = SemanticComponents.Opening.Unavailable(state, "Unavailable")
                val result = assertIs<SemanticQueryOutcome.Skipped>(query.rank("query"))
                assertEquals(reason, result.reason)
                assertEquals(state, result.status.state)
            }
            assertEquals(0, fake.starts)
        }

        @Test
        fun `applied files are complete and deduplicated before any fusion`() =
            SemanticQueryFixture(root) { workspace ->
                SemanticTestWorkspace.create(workspace)
                repeat(12) { SemanticTestWorkspace.write(workspace, "extra$it.txt", "file $it") }
            }.use { f ->
                val fake = FakeSemanticComponents(root.resolve("scratch"))
                SemanticWorkerSession(fake::open).use { session ->
                    SemanticQuery(f.current, session).use { query ->
                        val result = assertIs<SemanticQueryOutcome.Applied>(query.rank("query"))
                        val admitted = assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider))
                        val snapshot = admitted.snapshot
                        assertEquals(snapshot.entries.flatMap { it.artifacts }.toSet(), result.files.toSet())
                        assertEquals(result.files.distinct(), result.files)
                        assertTrue(result.files.size > 10)
                    }
                }
            }

        @Test
        fun `busy generation admission retains its own reason`() = fixture { f, fake, query ->
            FileChannel.open(f.directory.resolve("embeddings.lock"), StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    assertSkipped(query, SemanticQueryOutcome.Reason.ADMISSION_BUSY)
                }
            }
            assertEquals(0, fake.starts)
            f.assertNoLeases()
        }
    }

    private fun assertSkipped(query: SemanticQuery, reason: SemanticQueryOutcome.Reason) {
        val result = assertIs<SemanticQueryOutcome.Skipped>(query.rank("query"))
        assertEquals(reason, result.reason)
        assertEquals(SemanticState.WORKER_UNAVAILABLE, result.status.state)
    }

    private fun fixture(test: (SemanticQueryFixture, FakeSemanticComponents, SemanticQuery) -> Unit) =
        SemanticQueryFixture(root).use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            SemanticWorkerSession(fake::open).use { session ->
                SemanticQuery(f.current, session).use { query -> test(f, fake, query) }
            }
        }
}
