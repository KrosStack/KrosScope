package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndexingSessionTest {
    @Test
    fun `scattered cache misses share one full inference batch across accepted batches`() {
        val seed = SemanticFixture().apply { file() }.generation()
        val chunks = List(256) { index ->
            seed.chunks.single().chunk.copy(key = index.toString().padStart(3, '0'), text = "body $index")
        }
        val cached = chunks.filterIndexed { index, _ -> index % 16 != 0 }.map {
            seed.chunks.single().copy(chunk = it.copy(text = ""))
        }
        val previous = GenerationFile(seed.header.copy(chunkCount = cached.size), cached)
        for (generation in listOf(previous, null)) {
            val provider = FakeEmbeddingProvider()
            val session = SemanticIndexer().begin(provider, generation)
            chunks.chunked(16).forEach(session::accept)
            val result = session.finish()
            val missing = if (generation == null) chunks else chunks.filterIndexed { index, _ -> index % 16 == 0 }
            assertEquals(missing.map { it.text }.chunked(16), provider.batches)
            assertEquals(missing.size, session.embedded)
            assertEquals(chunks.size - missing.size, session.reused)
            assertEquals(chunks.map { it.key }, result.chunks.map { it.chunk.key })
            assertTrue(result.chunks.all { it.chunk.text.isEmpty() })
        }
    }

    @Test
    fun `duplicate digests select identical metadata in every batch order`() {
        val first = SemanticFixture().apply { file() }.generation().chunks.single().chunk.copy(text = "body")
        val artifact = first.artifact.copy(repository = RepositoryId("another"))
        val second = first.copy(artifact = artifact, nodes = listOf(NodeRef.OfArtifact(artifact)))
        val orders = listOf(
            listOf(listOf(first, second)),
            listOf(listOf(second, first)),
            listOf(listOf(first), listOf(second)),
            listOf(listOf(second), listOf(first)),
        )
        val generations = orders.map { batches ->
            val provider = FakeEmbeddingProvider()
            val session = SemanticIndexer().begin(provider, null)
            batches.forEach(session::accept)
            val generation = session.finish()
            assertEquals(1, provider.batches.flatten().size)
            generation
        }
        generations.forEach { assertContentEquals(generations.first().bytes(), it.bytes()) }
    }

    @Test
    fun `sessions reuse full identity and digest with current nodes and no retained text`() {
        val graph = SemanticFixture().apply { file() }
        val previous = graph.generation()
        val provider = FakeEmbeddingProvider()
        val current = previous.chunks.single().chunk.copy(
            text = "unused",
            nodes = listOf(NodeRef.OfSymbol(SymbolId("new"))),
        )
        val session = SemanticIndexer().begin(provider, previous)
        session.accept(listOf(current))
        session.accept(listOf(current.copy(nodes = listOf(NodeRef.OfSymbol(SymbolId("other"))))))
        val generation = session.finish()
        assertTrue(provider.batches.isEmpty())
        assertEquals(current.nodes + NodeRef.OfSymbol(SymbolId("other")), generation.chunks.single().chunk.nodes)
        assertEquals("", generation.chunks.single().chunk.text)
        val incompatible = SemanticIndexer().begin(
            provider,
            GenerationFile(
                previous.header.copy(identity = previous.header.identity.copy(revision = "changed")),
                previous.chunks,
            ),
        )
        incompatible.accept(listOf(current))
        incompatible.finish()
        assertEquals(listOf(listOf(current.text)), provider.batches)
    }

    @Test
    fun `failed and cancelled batches cannot finish a partial generation`() {
        val chunk = SemanticFixture().apply { file() }.generation().chunks.single().chunk.copy(text = "body")
        val delegate = FakeEmbeddingProvider()
        for (failure in listOf(IllegalStateException("failed"), CancellationException("cancelled"))) {
            val provider = object : EmbeddingProvider by delegate {
                override fun embed(texts: List<String>): List<FloatArray> = throw failure
            }
            val session = SemanticIndexer().begin(provider, null)
            session.accept(listOf(chunk))
            assertFailsWith<IllegalStateException> { session.finish() }
            assertFailsWith<IllegalStateException> { session.finish() }
        }
    }

    @Test
    fun `session rejects oversized exchanges and malformed vectors`() {
        val chunk = SemanticFixture().apply { file() }.generation().chunks.single().chunk.copy(text = "body")
        val delegate = FakeEmbeddingProvider()
        var calls = 0
        val provider = object : EmbeddingProvider by delegate {
            override fun embed(texts: List<String>): List<FloatArray> {
                calls++
                return listOf(FloatArray(identity.dimensions) { Float.NaN })
            }
        }
        val session = SemanticIndexer().begin(provider, null)
        assertFailsWith<IllegalArgumentException> { session.accept(List(17) { chunk }) }
        assertFailsWith<IllegalArgumentException> { session.accept(List(9) { chunk.copy(text = "x".repeat(32768)) }) }
        assertFailsWith<IllegalArgumentException> { session.accept(listOf(chunk.copy(text = "x".repeat(32769)))) }
        assertEquals(0, calls)
        session.accept(listOf(chunk))
        assertFailsWith<IllegalArgumentException> { session.finish() }
        assertFailsWith<IllegalStateException> { session.finish() }
        assertTrue(delegate.batches.isEmpty())
    }
}
