package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.semantic.store.PublishResult
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.graph.GraphReader
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CancellationException

public class SemanticIndexer {
    public fun begin(provider: EmbeddingProvider, previous: GenerationFile?): IndexingSession =
        IndexingSession(provider, previous)

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
            index(provider, chunks, lease?.generation, store)
        }
    }

    private fun index(
        provider: EmbeddingProvider,
        chunks: List<Chunk>,
        previous: GenerationFile?,
        store: GenerationStore,
    ): IndexingReport {
        val oldKeys = previous?.takeIf { it.header.identity == provider.identity }?.chunks.orEmpty()
            .map { it.chunk.key }.toSet()
        val missing = chunks.count { it.key !in oldKeys }
        val keys = chunks.map { it.key }.toSet()
        val dropped = oldKeys.count { it !in keys }
        val session = begin(provider, previous)
        val generation = prepare(session, chunks)
        if (generation == null) {
            return IndexingReport(
                session.embedded,
                keys.count { it in oldKeys },
                dropped,
                missing,
                PublishResult.Deferred("Embedding provider failed; no generation was published"),
            )
        }
        val publication = store.publish(generation)
        val behind = if (publication is PublishResult.Published) 0 else missing
        return IndexingReport(session.embedded, session.reused, dropped, behind, publication)
    }

    private fun prepare(session: IndexingSession, chunks: List<Chunk>): GenerationFile? = try {
        for (chunk in chunks) session.accept(listOf(chunk))
        session.finish()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: IOException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
