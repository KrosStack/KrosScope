package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.semantic.store.PublishResult
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemanticIndexerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `second run embeds nothing and generation bytes stay identical`() {
        val graph = SemanticFixture().apply { file() }
        val provider = FakeEmbeddingProvider()
        val indexer = SemanticIndexer()
        val first = indexer.update(provider, graph.reader, graph, directory)
        val firstName = assertIs<PublishResult.Published>(first.publication).generation
        val bytes = Files.readAllBytes(directory.resolve(firstName))
        val second = indexer.update(provider, graph.reader, graph, directory)
        assertEquals(0, second.embedded)
        assertEquals(first.embedded, second.reused)
        assertEquals(first.publication, second.publication)
        assertContentEquals(bytes, Files.readAllBytes(directory.resolve(firstName)))
        assertEquals(1, provider.batches.size)
    }

    @Test
    fun `body edit embeds only its merged sibling chunk and reuses unchanged outlines`() {
        val graph = SemanticFixture()
        val file = graph.file()
        graph.symbol(file, "a", SymbolCategory.CALLABLE, 2)
        graph.symbol(file, "b", SymbolCategory.CALLABLE, 3)
        val provider = FakeEmbeddingProvider()
        val indexer = SemanticIndexer()
        indexer.update(provider, graph.reader, graph, directory)
        graph.file(text = "class Sample {\nfun a() = 9\nfun b() = 2\n}")
        val result = indexer.update(provider, graph.reader, graph, directory)
        assertEquals(1, result.embedded)
        assertEquals(1, result.reused)
        assertEquals(1, result.dropped)
    }

    @Test
    fun `provider failure part way reports behind and publishes nothing`() {
        val graph = SemanticFixture().apply { file("old.kt") }
        val provider = FakeEmbeddingProvider()
        val indexer = SemanticIndexer()
        indexer.update(provider, graph.reader, graph, directory)
        val manifest = Files.readAllBytes(directory.resolve("embeddings.json"))
        repeat(300) { graph.file("new$it.kt") }
        provider.failAfter = 2
        val report = indexer.update(provider, graph.reader, graph, directory)
        assertEquals(256, report.embedded)
        assertEquals(300, report.behind)
        assertIs<PublishResult.Deferred>(report.publication)
        assertContentEquals(manifest, Files.readAllBytes(directory.resolve("embeddings.json")))
        GenerationStore(directory).open(provider.id).use { assertEquals(1, assertNotNull(it).generation.chunks.size) }
        assertTrue(provider.batches.all { it.size <= 256 && it.sumOf { text -> text.toByteArray().size } <= 262144 })
    }

    @Test
    fun `model change embeds everything instead of reusing incompatible vectors`() {
        val graph = SemanticFixture().apply { file() }
        val provider = FakeEmbeddingProvider()
        val store = GenerationStore(directory)
        val old = graph.generation()
        store.publish(
            io.github.krosstack.kscope.semantic.store.GenerationFile(
                old.header.copy(identity = old.header.identity.copy(revision = "old")),
                old.chunks,
            ),
        )
        val report = SemanticIndexer().update(provider, graph.reader, graph, directory)
        assertEquals(1, report.embedded)
        assertEquals(0, report.reused)
    }

    @Test
    fun `large standalone body edit leaves other declarations and outlines reusable`() {
        val graph = SemanticFixture()
        val body = "word ".repeat(100)
        val file = graph.file(text = "$body\n$body")
        graph.symbol(file, "a", SymbolCategory.CALLABLE)
        graph.symbol(file, "b", SymbolCategory.CALLABLE, 2)
        val provider = FakeEmbeddingProvider()
        val indexer = SemanticIndexer()
        indexer.update(provider, graph.reader, graph, directory)
        graph.file(text = "edit ${body.drop(5)}\n$body")
        val result = indexer.update(provider, graph.reader, graph, directory)
        assertEquals(1, result.embedded)
        assertEquals(2, result.reused)
        assertEquals(1, result.dropped)
    }

    @Test
    fun `provider programming defect propagates from update`() {
        val graph = SemanticFixture().apply { file() }
        val delegate = FakeEmbeddingProvider()
        val provider = object : io.github.krosstack.kscope.spi.embedding.EmbeddingProvider by delegate {
            override fun embed(texts: List<String>): List<FloatArray> = throw NullPointerException("Provider defect")
        }
        assertFailsWith<NullPointerException> {
            SemanticIndexer().update(provider, graph.reader, graph, directory)
        }
    }

    @Test
    fun `batches obey byte limit before reaching chunk limit`() {
        val graph = SemanticFixture()
        repeat(20) { index ->
            val file = graph.file("source$index.kt", "x".repeat(30000))
            graph.symbol(file, "s$index", SymbolCategory.CALLABLE)
        }
        val provider = FakeEmbeddingProvider()
        SemanticIndexer().update(provider, graph.reader, graph, directory)
        assertTrue(provider.batches.size >= 3)
        assertTrue(provider.batches.all { batch -> batch.sumOf { it.toByteArray().size } <= 262144 })
    }
}
