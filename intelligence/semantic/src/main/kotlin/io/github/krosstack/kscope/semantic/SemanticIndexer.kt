package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.search.ScoredChunk
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationHeader
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.semantic.store.PublishResult
import io.github.krosstack.kscope.semantic.store.Quantized
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.graph.GraphReader
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CancellationException

public class SemanticIndexer {
    public fun update(
        provider: EmbeddingProvider,
        reader: GraphReader,
        files: WorkspaceFiles,
        directory: Path,
    ): IndexingReport {
        val builder = ChunkBuilder(provider::cap)
        val chunks = builder.build(reader, files).groupBy { it.key }.values.map { same ->
            same.first().copy(nodes = same.flatMap { it.nodes }.distinct())
        }.sortedBy { it.key }
        val store = GenerationStore(directory)
        return store.open(provider.id).use { lease ->
            val previous = lease?.generation?.takeIf {
                it.header.identity == provider.identity
            }?.chunks.orEmpty().associateBy { it.chunk.key }
            index(provider, chunks, previous, store)
        }
    }

    private fun index(
        provider: EmbeddingProvider,
        chunks: List<Chunk>,
        previous: Map<String, ScoredChunk>,
        store: GenerationStore,
    ): IndexingReport {
        val reused = chunks.filter { it.key in previous }.map { ScoredChunk(it, previous.getValue(it.key).vector) }
        val missing = chunks.filterNot { it.key in previous }
        val keys = chunks.map { it.key }.toSet()
        val dropped = previous.keys.count { it !in keys }
        val embedded = mutableListOf<ScoredChunk>()
        for (batch in batches(missing)) {
            val vectors = embed(provider, batch) ?: return IndexingReport(
                embedded.size,
                reused.size,
                dropped,
                missing.size,
                PublishResult.Deferred("Embedding provider failed; no generation was published"),
            )
            embedded += vectors
        }
        val header = GenerationHeader(
            provider.id,
            provider.identity,
            provider.identity.dimensions,
            chunks.size,
        )
        val publication = store.publish(GenerationFile(header, reused + embedded))
        val behind = if (publication is PublishResult.Published) 0 else missing.size
        return IndexingReport(embedded.size, reused.size, dropped, behind, publication)
    }

    private fun embed(provider: EmbeddingProvider, chunks: List<Chunk>): List<ScoredChunk>? = try {
        val vectors = provider.embed(chunks.map { it.text })
        require(vectors.size == chunks.size && vectors.all { it.size == provider.identity.dimensions })
        chunks.zip(vectors).map { (chunk, vector) -> ScoredChunk(chunk, Quantized.quantize(vector)) }
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: IOException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun batches(chunks: List<Chunk>): List<List<Chunk>> {
        val batches = mutableListOf<List<Chunk>>()
        var pending = emptyList<Chunk>()
        var bytes = 0
        for (chunk in chunks) {
            val size = chunk.text.toByteArray().size
            if (pending.size == MAX_BATCH_CHUNKS || bytes + size > MAX_BATCH_BYTES) {
                batches += pending
                pending = emptyList()
                bytes = 0
            }
            pending += chunk
            bytes += size
        }
        if (pending.isNotEmpty()) batches += pending
        return batches
    }

    private companion object {
        const val MAX_BATCH_CHUNKS = 256
        const val MAX_BATCH_BYTES = 256 * 1024
    }
}
