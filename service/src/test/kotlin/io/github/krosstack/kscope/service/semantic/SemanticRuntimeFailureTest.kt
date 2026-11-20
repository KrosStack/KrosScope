package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace.LIB
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemanticRuntimeFailureTest {
    @TempDir
    lateinit var root: Path

    private val scratch get() = root.resolveSibling("${root.fileName}-scratch")

    @Test
    fun `a warm cleanup failure does not abort a foreground scan or bypass its retry delay`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            fake.closeFailure = IOException("Reservation locked")
            SemanticTestWorkspace.write(root, LIB, "class Lib { fun first() = 9 }\n")
            val result = scope.scan()
            assertTrue(result.indexLocation.isNotEmpty() && scope.isIndexed())
            assertTrue(scope.graph().artifacts().any { it.ref.path.value == LIB })
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
            assertTrue(scope.semanticNotice().orEmpty().contains("Reservation locked"))
            assertFalse(scope.semantic.advance())
            assertEquals(1, fake.opened)
            assertEquals(1, fake.closed)
        }
        assertEquals(1, fake.closed)
    }

    @Test
    fun `an indexing failure closes its run before a failing worker cleanup`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { it.scan() }
        val closed = fake.closed
        var leasesAtCleanup = -1L
        fake.onClose = { leasesAtCleanup = heldLeases() }
        fake.closeFailure = IOException("Reservation locked")
        fake.provider.failEmbedding = true
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            SemanticTestWorkspace.write(root, LIB, "class Lib { fun first() = 9 }\n")
            scope.scan()
            assertTrue(scope.semantic.advance())
            assertEquals(1L, heldLeases())
            while (scope.semantic.advance()) continue
            assertEquals(0L, heldLeases())
            assertEquals(0L, leasesAtCleanup)
            assertEquals(closed + 1, fake.closed)
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
            assertTrue(scope.semanticNotice().orEmpty().contains("Worker unavailable"))
            assertTrue(scope.semanticNotice().orEmpty().contains("Reservation locked"))
            assertFalse(scope.semantic.advance())
        }
        assertEquals(closed + 1, fake.closed)
    }

    @Test
    fun `a refresh failure in advance closes the active run before worker cleanup`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            var refreshFails = false
            val workspace = SemanticWorkspace(
                scope::isIndexed,
                scope::graph,
                scope::readWorkspaceFiles,
                { 0L },
                { if (refreshFails) throw IOException("Refresh unreadable") else false },
            )
            SemanticRuntime(root.resolve(".kscope"), fake::open, fake::unavailable, workspace).use { runtime ->
                Files.delete(root.resolve(".kscope/embeddings-chunks.json"))
                while (fake.starts == 1) assertTrue(runtime.advance())
                assertEquals(1L, heldLeases())
                var leasesAtCleanup = -1L
                fake.onClose = { leasesAtCleanup = heldLeases() }
                fake.closeFailure = IOException("Reservation locked")
                refreshFails = true
                assertFalse(runtime.advance())
                assertEquals(0L, heldLeases())
                assertEquals(0L, leasesAtCleanup)
                assertEquals(1, fake.closed)
                assertTrue(runtime.notice.orEmpty().contains("Refresh unreadable"))
                assertTrue(runtime.notice.orEmpty().contains("Reservation locked"))
            }
            assertEquals(1, fake.closed)
        }
        assertEquals(2, fake.closed)
    }

    @Test
    fun `invalidation attempts worker cleanup even when closing the run fails`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            var revision = 0L
            val workspace = SemanticWorkspace(
                scope::isIndexed,
                scope::graph,
                scope::readWorkspaceFiles,
                { revision },
                { false },
            )
            SemanticRuntime(root.resolve(".kscope"), fake::open, fake::unavailable, workspace).use { runtime ->
                Files.delete(root.resolve(".kscope/embeddings-chunks.json"))
                while (fake.starts == 1) assertTrue(runtime.advance())
                val lease = Files.list(root.resolve(".kscope/embeddings-leases")).use { it.toList().single() }
                Files.delete(lease)
                Files.createDirectory(lease)
                val obstruction = Files.writeString(lease.resolve("held"), "held")
                fake.closeFailure = IOException("Reservation locked")
                revision++
                runtime.invalidate()
                assertEquals(1, fake.closed)
                assertEquals(SemanticState.WORKER_UNAVAILABLE, runtime.status.state)
                assertTrue(runtime.notice.orEmpty().contains(lease.toString()))
                assertTrue(runtime.notice.orEmpty().contains("Reservation locked"))
                assertFalse(runtime.advance())
                assertEquals(2, fake.opened)
                Files.delete(obstruction)
                Files.delete(lease)
            }
            assertEquals(1, fake.closed)
        }
        assertEquals(2, fake.closed)
    }

    @Test
    fun `a failure that is not an io failure leaves the scan intact`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        fake.openFailure = IllegalStateException("Budget directory unusable")
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            val result = scope.scan()
            assertTrue(result.indexLocation.isNotEmpty() && scope.isIndexed())
            assertTrue(scope.semanticNotice().orEmpty().contains("Budget directory unusable"))
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
        }
    }

    @Test
    fun `an unavailable component is retried after the delay and not before`() {
        var clock = 1_000L
        val problem = SemanticComponents.Opening.Unavailable(SemanticState.WORKER_UNAVAILABLE, "Budget refused")
        var opened = 0
        val unused = { error("unused") }
        val workspace = SemanticWorkspace({ true }, unused, unused, { 0L }, { false })
        val runtime = SemanticRuntime(root, { problem.also { opened++ } }, { problem }, workspace) { clock }
        assertTrue(!runtime.advance())
        clock += 60_000L
        runtime.advance()
        assertEquals(1, opened)
        clock += 5 * 60_000L
        runtime.advance()
        assertEquals(2, opened)
    }

    @Test
    fun `a job whose reservation is lost ends failed and releases its worker`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            while (fake.starts == 0) scope.semantic.advance()
            assertEquals(1, fake.starts)
            fake.lost = "The worker's memory reservation was released"
            scope.semantic.advance()
            assertEquals(1, fake.closed)
            assertTrue(scope.semanticNotice().orEmpty().contains("reservation was released"))
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
        }
    }

    @Test
    fun `a source that changed during the job fails it and publishes nothing`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            SemanticTestWorkspace.write(root, LIB, "class Lib {\n    fun first() = 9\n}\n")
            scope.semantic.finish()
            assertTrue(scope.semanticNotice().orEmpty().contains("no longer matches the scan"))
            assertTrue(!Files.exists(root.resolve(".kscope/embeddings.json")))
        }
    }

    @Test
    fun `a lease is not left behind when the job cannot start`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { it.scan() }
        val unreadable = { error("graph unreadable") }
        val workspace = SemanticWorkspace({ true }, unreadable, unreadable, { 0L }, { false })
        val runtime = SemanticRuntime(root.resolve(".kscope"), fake::open, fake::unavailable, workspace)
        runtime.advance()
        assertNotNull(runtime.notice)
        val leases = root.resolve(".kscope/embeddings-leases")
        val held = if (Files.isDirectory(leases)) Files.list(leases).use { it.count() } else 0L
        assertEquals(0L, held)
    }

    private fun heldLeases(): Long = Files.list(root.resolve(".kscope/embeddings-leases")).use { it.count() }
}
