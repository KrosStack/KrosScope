package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.indexRevision
import io.github.krosstack.kscope.service.refreshIndex
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace.OTHER
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticRuntimeFreshnessTest {
    @TempDir
    lateinit var root: Path

    private val scratch get() = root.resolveSibling("${root.fileName}-scratch")

    @Test
    fun `a warm cleanup failure does not abort refresh after an external scan`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { resident ->
            resident.scan()
            val revision = resident.indexRevision()
            SemanticTestWorkspace.open(root, FakeSemanticComponents(scratch), SemanticIndexingMode.OFF).use {
                SemanticTestWorkspace.write(root, OTHER, "class Other { fun third() = 8 }\n")
                it.scan()
            }
            fake.closeFailure = IOException("Reservation locked")
            resident.refreshIndex()
            assertTrue(resident.indexRevision() > revision)
            assertEquals(SemanticState.WORKER_UNAVAILABLE, resident.semanticStatus().state)
            assertTrue(resident.semanticNotice().orEmpty().contains("Reservation locked"))
            assertFalse(resident.semantic.advance())
            assertEquals(1, fake.opened)
            assertEquals(1, fake.closed)
        }
        assertEquals(1, fake.closed)
    }

    @Test
    fun `an idle workspace is never reported fresh without a job that proved it`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
        }
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.OFF).use { scope ->
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
            SemanticTestWorkspace.write(root, OTHER, "class Other {\n    fun third() = 7\n}\n")
            scope.scan()
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
        }
    }

    @Test
    fun `a second scan of an unchanged workspace starts no worker`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            assertEquals(1, fake.starts)
            scope.scan()
            assertEquals(1, fake.starts)
            assertEquals(2, fake.opened)
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
        }
    }

    @Test
    fun `an external scan is seen before the next step`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { resident ->
            resident.scan()
            while (fake.starts == 0) resident.semantic.advance()
            SemanticTestWorkspace.open(root, FakeSemanticComponents(scratch), SemanticIndexingMode.OFF).use {
                SemanticTestWorkspace.write(root, OTHER, "class Other {\n    fun third() = 8\n}\n")
                it.scan()
            }
            resident.semantic.advance()
            assertEquals(1, fake.closed)
            assertEquals(2, fake.opened)
        }
    }

    @Test
    fun `a manifest changed by another process replans instead of publishing over it`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(scratch)
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.DEFERRED).use { scope ->
            scope.scan()
            scope.semantic.advance()
            val foreign = "embeddings-${fake.provider.id}-${"a".repeat(64)}.bin"
            Files.writeString(root.resolve(".kscope/embeddings.json"), """{"${fake.provider.id}":"$foreign"}""")
            scope.semantic.finish()
            assertEquals(2, fake.opened)
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
            assertTrue(!Files.readString(root.resolve(".kscope/embeddings.json")).contains(foreign))
        }
    }
}
