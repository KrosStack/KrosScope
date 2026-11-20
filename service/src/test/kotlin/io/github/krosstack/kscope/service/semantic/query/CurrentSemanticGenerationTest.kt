package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.semantic.SemanticIndexer
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.service.semantic.SemanticChunkCache
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CurrentSemanticGenerationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `complete unchanged inventory proves current keys without worker calls`() = fixture { f ->
        val snapshot = ready(f)
        assertEquals(0, snapshot.behindChunks)
        assertEquals(f.revision, snapshot.identity.graphRevision)
        assertEquals(f.component, snapshot.identity.componentFingerprint)
        assertEquals(f.provider.identity, snapshot.identity.modelIdentity)
        assertEquals(f.provider.id, snapshot.identity.provider)
        assertEquals(f.store.current(f.provider.id), snapshot.identity.generationName)
        assertEquals(f.inventory.flatMap { it.chunks }.map { it.key }.distinct().size, snapshot.entries.size)
        assertSame(snapshot, ready(f))
        assertEquals(0, f.provider.capCalls)
        assertTrue(f.provider.embedded.isEmpty())
        f.current.close()
        f.assertNoLeases()
        assertIs<CurrentSemanticGeneration.Opening.Unavailable>(f.current.open(f.provider))
        f.assertNoLeases()
    }

    @Test
    fun `older generation joins current keys in key order and counts distinct missing vectors`() = fixture { f ->
        val published = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        val kept = published.chunks.take(2).reversed()
        val obsolete = kept.first().copy(chunk = kept.first().chunk.copy(key = "f".repeat(64)))
        val stale = kept.map {
            it.copy(
                chunk = it.chunk.copy(nodes = emptyList(), artifact = obsoleteRef(it.chunk.artifact)),
            )
        }
        f.store.publish(GenerationFile(published.header.copy(chunkCount = 3), stale + obsolete))
        f.cache.write(f.provider.identity, f.inventory.map { it.copy(chunks = it.chunks + it.chunks) })
        val snapshot = ready(f)
        val keys = f.inventory.flatMap { it.chunks }.map { it.key }.toSet()
        assertEquals((keys - kept.map { it.chunk.key }.toSet()).size, snapshot.behindChunks)
        assertEquals(2, snapshot.entries.size)
        snapshot.entries.zip(kept.sortedBy { it.chunk.key }).forEach { (entry, stored) ->
            assertContentEquals(stored.vector.bytes, entry.vector.bytes)
            assertEquals(stored.vector.scale, entry.vector.scale)
            assertTrue(entry.artifacts.none { it.path.value == "deleted.kt" })
            assertTrue(stored.chunk.artifact in entry.artifacts)
        }
    }

    @Test
    fun `missing foreign stale ambiguous and unreadable inventory entries are unproven`() = fixture { f ->
        val first = f.inventory.first { it.chunks.isNotEmpty() }
        val excluded = f.inventory.first { it.artifact.path.value == "image.png" }
        assertTrue(excluded.chunks.isEmpty())
        val rest = f.inventory - first
        val variants = listOf(
            rest,
            f.inventory - excluded,
            rest + first.copy(artifact = obsoleteRef(first.artifact)),
            rest + first.copy(contentHash = "changed"),
            rest + first.copy(symbols = "0".repeat(64)),
            f.inventory + first,
            rest + first.copy(chunks = listOf(first.chunks.first().copy(kind = "FUTURE"))),
        )
        for (inventory in variants) {
            f.cache.write(f.provider.identity, f.inventory)
            ready(f)
            f.cache.write(f.provider.identity, inventory)
            assertIs<CurrentSemanticGeneration.Opening.Unproven>(f.current.open(f.provider))
            f.assertNoLeases()
        }
        f.cache.write(f.provider.identity, f.inventory)
        ready(f)
        val cacheFile = f.directory.resolve("embeddings-chunks.json")
        Files.writeString(cacheFile, Files.readString(cacheFile).replace(first.artifact.path.value, "../outside.kt"))
        assertIs<CurrentSemanticGeneration.Opening.Unproven>(f.current.open(f.provider))
        f.assertNoLeases()
        f.cache.write(f.provider.identity.copy(revision = "foreign"), f.inventory)
        assertIs<CurrentSemanticGeneration.Opening.Unproven>(f.current.open(f.provider))
        Files.delete(f.directory.resolve("embeddings-chunks.json"))
        assertIs<CurrentSemanticGeneration.Opening.Unproven>(f.current.open(f.provider))
        f.assertNoLeases()
    }

    @Test
    fun `different provider and every model identity field refuse admission and release leases`() = fixture { f ->
        val identity = f.provider.identity
        val identities = listOf(
            identity.copy(name = "other"),
            identity.copy(revision = "other"),
            identity.copy(dimensions = 4),
            identity.copy(quantization = "other"),
            identity.copy(chunkBuilderVersion = 2),
        )
        for (model in identities) {
            ready(f)
            val foreign = object : EmbeddingProvider by f.provider {
                override val identity = model
            }
            assertIs<CurrentSemanticGeneration.Opening.Unavailable>(f.current.open(foreign))
            f.assertNoLeases()
        }
        ready(f)
        val foreign = object : EmbeddingProvider by f.provider {
            override val id = "foreign"
        }
        assertIs<CurrentSemanticGeneration.Opening.Unavailable>(f.current.open(foreign))
        f.assertNoLeases()
    }

    @Test
    fun `manifest inventory graph and component changes invalidate cached snapshots`() = fixture { f ->
        var previous = ready(f)
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        f.store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1)))
        val published = ready(f)
        assertNotSame(previous, published)
        assertNotEquals(previous.identity.generationName, published.identity.generationName)
        previous = published
        f.revision++
        assertNotSame(previous, ready(f))
        previous = ready(f)
        f.component = "component-b"
        assertNotSame(previous, ready(f))
        previous = ready(f)
        f.cache.write(f.provider.identity, f.inventory.reversed())
        assertSame(previous, ready(f))
        val updated = f.inventory.map { record ->
            record.copy(chunks = record.chunks.map { it.copy(nodes = emptyList()) })
        }
        f.cache.write(f.provider.identity, updated)
        val changed = ready(f)
        assertNotSame(previous, changed)
        assertNotEquals(previous.identity.inventoryFingerprint, changed.identity.inventoryFingerprint)
        f.current.invalidate()
        f.assertNoLeases()
        assertNotSame(previous, ready(f))
    }

    @Test
    fun `inventory and graph changes during admission discard the leased candidate`() = fixture { f ->
        val changes = listOf<() -> Unit>(
            { Files.writeString(f.directory.resolve("embeddings-chunks.json"), "{") },
            { f.revision++ },
        )
        for (change in changes) {
            f.cache.write(f.provider.identity, f.inventory)
            var reads = 0
            CurrentSemanticGeneration(f.workspace, f.directory) {
                if (++reads == 2) change()
                f.component
            }.use { current ->
                assertIs<CurrentSemanticGeneration.Opening.Unavailable>(current.open(f.provider))
                f.assertNoLeases()
            }
        }
    }

    @Test
    fun `equal key owners are all credited and the representative agrees with clean indexing`() = fixture { f ->
        val key = f.inventory.flatMap { it.chunks }.first().key
        val inventory = f.inventory.map { record ->
            record.copy(chunks = listOf(SemanticChunkCache.Entry(key, ChunkKind.FILE.name, emptyList())))
        }.reversed()
        f.cache.write(f.provider.identity, inventory)
        val chunks = inventory.flatMap { it.toChunks() }
        f.store.publish(f.generation(chunks))
        val session = SemanticIndexer().begin(f.provider, null)
        chunks.reversed().forEach { session.accept(listOf(it)) }
        val expected = session.finish().chunks.single().chunk
        val snapshot = ready(f)
        val canonical = inventory.minBy { KscopeJson.encodeToString(it.artifact) }.artifact
        assertEquals(canonical, expected.artifact)
        assertEquals(inventory.map { it.artifact }.toSet(), snapshot.entries.single().artifacts)
        assertEquals(0, snapshot.behindChunks)
        f.current.invalidate()
        f.assertNoLeases()
        val withNodes = inventory.map {
            it.copy(
                chunks = listOf(it.chunks.single().copy(nodes = listOf(NodeRef.OfArtifact(it.artifact)))),
            )
        }
        f.cache.write(f.provider.identity, withNodes)
        assertEquals(inventory.map { it.artifact }.toSet(), ready(f).entries.single().artifacts)
    }

    @Test
    fun `no current vector intersection is unavailable and releases its lease`() = fixture { f ->
        ready(f)
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        val chunk = generation.chunks.first().let { it.copy(chunk = it.chunk.copy(key = "f".repeat(64))) }
        f.store.publish(GenerationFile(generation.header.copy(chunkCount = 1), listOf(chunk)))
        assertIs<CurrentSemanticGeneration.Opening.Unavailable>(f.current.open(f.provider))
        f.assertNoLeases()
    }

    private fun ready(f: SemanticQueryFixture) =
        assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider)).snapshot

    private fun obsoleteRef(ref: io.github.krosstack.kscope.model.ArtifactRef) = ref.copy(path = RepoPath("deleted.kt"))

    private fun fixture(test: (SemanticQueryFixture) -> Unit) = SemanticQueryFixture(root).use(test)
}
