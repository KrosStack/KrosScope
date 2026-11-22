package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace.LIB
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace.OTHER
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticIndexingTest {
    @TempDir
    lateinit var root: Path

    private val components get() = FakeSemanticComponents(root.resolveSibling("${root.fileName}-scratch"))

    private fun manifest() = Files.readAllBytes(root.resolve(".kscope/embeddings.json"))

    @Test
    fun `next revision embeds only its changed chunk`() {
        SemanticTestWorkspace.create(root)
        val fake = components
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            val first = fake.provider.embedded.size
            assertTrue(first > 0)
            fake.provider.embedded.clear()
            SemanticTestWorkspace.write(root, OTHER, "class Other {\n    fun third() = 4\n}\n")
            scope.scan()
            assertTrue(fake.provider.embedded.isNotEmpty() && fake.provider.embedded.size < first)
            assertTrue(fake.provider.embedded.all { "= 4" in it || "Other" in it })
        }
        TemporaryTree.delete(root.resolveSibling("${root.fileName}-scratch"))
    }

    @Test
    fun `unchanged artifact inventory makes no cap calls`() {
        SemanticTestWorkspace.create(root)
        val fake = components
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            val before = manifest()
            fake.provider.capCalls = 0
            fake.provider.embedded.clear()
            scope.scan()
            assertEquals(0, fake.provider.capCalls)
            assertTrue(fake.provider.embedded.isEmpty())
            assertContentEquals(before, manifest())
        }
    }

    @Test
    fun `signature and documentation changes invalidate artifact keys`() {
        SemanticTestWorkspace.create(root)
        val fake = components
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            val before = manifest()
            fake.provider.capCalls = 0
            val documented = "class Lib {\n    /** Doc. */\n    fun first(x: Int) = 1\n    fun second() = 2\n}\n"
            SemanticTestWorkspace.write(root, LIB, documented)
            scope.scan()
            assertTrue(fake.provider.capCalls > 0)
            assertTrue(!before.contentEquals(manifest()))
        }
    }

    @Test
    fun `a cache of another model identity reads as empty`() {
        val cache = SemanticChunkCache(root.resolve("embeddings-chunks.json"))
        val identity = ModelIdentity("a", "1", 2, "int8", 1)
        cache.write(identity, emptyList())
        assertTrue(cache.read(identity).isEmpty())
        Files.writeString(root.resolve("embeddings-chunks.json"), "{")
        assertTrue(cache.read(identity).isEmpty())
        assertTrue(cache.read(identity.copy(revision = "2")).isEmpty())
    }

    @Test
    fun `an unknown chunk kind in the cache is a miss and not a failure`() {
        val file = root.resolve("embeddings-chunks.json")
        val cache = SemanticChunkCache(file)
        val identity = ModelIdentity("a", "1", 2, "int8", 1)
        val ref = ArtifactRef(RepositoryId("repo"), RepoPath("A.kt"))
        val chunk = Chunk("key", ChunkKind.FILE, "", listOf(NodeRef.OfArtifact(ref)), ref)
        cache.write(identity, listOf(SemanticChunkCache.ArtifactKeys.of(ref, "hash", "symbols", listOf(chunk))))
        assertEquals(1, cache.read(identity).size)
        Files.writeString(file, Files.readString(file).replace("\"FILE\"", "\"LATER\""))
        assertTrue(cache.read(identity).isEmpty())
    }

    @Test
    fun `failure leaves the previous manifest byte identical and a later scan recovers`() {
        SemanticTestWorkspace.create(root)
        val fake = components
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { scope ->
            scope.scan()
            val before = manifest()
            fake.provider.failEmbedding = true
            SemanticTestWorkspace.write(root, OTHER, "class Other {\n    fun third() = 5\n}\n")
            scope.scan()
            assertContentEquals(before, manifest())
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
            assertTrue(scope.semanticNotice().orEmpty().contains("failed"))
            fake.provider.failEmbedding = false
            SemanticTestWorkspace.write(root, OTHER, "class Other {\n    fun third() = 6\n}\n")
            scope.scan()
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
            assertTrue(!before.contentEquals(manifest()))
        }
    }

    @Test
    fun `rebuild clears derived vectors and verify ignores them`() {
        SemanticTestWorkspace.create(root)
        SemanticTestWorkspace.open(root, components, SemanticIndexingMode.FOREGROUND).use { it.scan() }
        assertTrue(Files.exists(root.resolve(".kscope/embeddings-chunks.json")))
        SemanticTestWorkspace.open(root, components, SemanticIndexingMode.DEFERRED).use { scope ->
            val result = scope.scan(rebuild = true, verify = true)
            assertEquals(true, result.verification?.matches)
            assertTrue(!Files.exists(root.resolve(".kscope/embeddings.json")))
            assertTrue(!Files.exists(root.resolve(".kscope/embeddings-chunks.json")))
            assertEquals(SemanticState.OFF, scope.semanticStatus(MlMode.OFF).state)
        }
    }
}
