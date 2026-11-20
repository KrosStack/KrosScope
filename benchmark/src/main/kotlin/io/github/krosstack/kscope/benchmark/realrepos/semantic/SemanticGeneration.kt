package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.semantic.SemanticIndexer
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.semantic.store.PublishResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path

internal class SemanticGeneration(private val provider: EmbeddingProvider) {
    private val builder = ChunkBuilder(provider::cap)
    var embedded: Int = 0
        private set
    var reused: Int = 0
        private set

    fun update(scope: KrosScope, workspace: Path, directory: Path): GenerationFile {
        val store = GenerationStore(directory)
        val expected = store.current(provider.id)
        val previous = store.open(provider.id).use { it?.generation }
        val session = SemanticIndexer().begin(provider, previous)
        val graph = scope.graph()
        for (artifact in graph.artifacts().sortedBy { KscopeJson.encodeToString(it.ref) }) {
            val text = source(artifact, workspace) ?: continue
            val symbols = graph.symbols(SymbolQuery(artifact = artifact.ref, limit = Int.MAX_VALUE))
            val cursor = builder.cursor(artifact, symbols, text)
            while (!cursor.complete) session.accept(cursor.next(BATCH_CHUNKS, BATCH_BYTES))
        }
        val generation = session.finish()
        store.prepare(generation).use { check(store.publish(it, expected) is PublishResult.Published) }
        val oldKeys = previous?.takeIf { it.header.identity == provider.identity }?.chunks.orEmpty()
            .map { it.chunk.key }.toSet()
        reused = generation.chunks.count { it.chunk.key in oldKeys }
        embedded = generation.chunks.size - reused
        return generation
    }

    private fun source(artifact: Artifact, workspace: Path): String? {
        if (artifact.generated != null || artifact.sizeBytes > MAX_SOURCE_BYTES) return null
        val path = workspace.resolve(artifact.ref.path.value).normalize()
        require(path.startsWith(workspace) && path.toRealPath().startsWith(workspace))
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_SOURCE_BYTES + 1) }
        return if (bytes.size > MAX_SOURCE_BYTES) {
            null
        } else {
            require(SemanticCheckpoint.digest(bytes) == artifact.contentHash.value) { "Indexed source changed" }
            decoded(bytes)
        }
    }

    private fun decoded(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().takeUnless { '\u0000' in it }
    } catch (_: CharacterCodingException) {
        null
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 2 * 1024 * 1024
        const val BATCH_CHUNKS = 16
        const val BATCH_BYTES = 256 * 1024
    }
}
