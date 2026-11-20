package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.search.ScoredChunk
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationHeader
import io.github.krosstack.kscope.semantic.store.Quantized
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider

public class IndexingSession internal constructor(private val provider: EmbeddingProvider, previous: GenerationFile?) {
    private val identity = provider.identity
    private val previous = previous?.takeIf { it.header.provider == provider.id && it.header.identity == identity }
        ?.chunks.orEmpty().associateBy { it.chunk.key }
    private val chunks = linkedMapOf<String, Chunk>()
    private val vectors = mutableMapOf<String, Quantized>()
    private val pending = linkedMapOf<String, String>()
    private var pendingBytes = 0
    private var usable = true
    internal var embedded = 0
        private set
    internal val reused: Int get() = vectors.size - embedded

    /** Missing texts accumulate across calls; [finish] embeds the last partial batch. */
    public fun accept(chunks: List<Chunk>) {
        check(usable && provider.identity == identity)
        require(chunks.size <= MAX_BATCH_CHUNKS)
        require(chunks.all { it.text.length <= MAX_CHUNK_BYTES })
        val sizes = chunks.map { it.text.toByteArray(Charsets.UTF_8).size }
        require(sizes.all { it <= MAX_CHUNK_BYTES } && sizes.sum() <= MAX_BATCH_BYTES)
        usable = false
        for (chunk in chunks) {
            val existing = this.chunks[chunk.key]
            if (existing == null) {
                val reused = previous[chunk.key]?.vector
                if (reused == null) queue(chunk) else vectors[chunk.key] = reused
            }
            val nodes = (existing?.nodes.orEmpty() + chunk.nodes).distinct()
                .sortedBy { KscopeJson.encodeToString(it) }
            val representative = listOfNotNull(existing, chunk)
                .minWith(compareBy({ KscopeJson.encodeToString(it.artifact) }, { it.kind.name }))
            this.chunks[chunk.key] = representative.copy(text = "", nodes = nodes)
        }
        usable = true
    }

    public fun finish(): GenerationFile {
        check(usable && provider.identity == identity)
        usable = false
        flush()
        return GenerationFile(
            GenerationHeader(provider.id, identity, identity.dimensions, chunks.size),
            chunks.values.sortedBy { it.key }.map { ScoredChunk(it, vectors.getValue(it.key)) },
        )
    }

    private fun queue(chunk: Chunk) {
        val bytes = chunk.text.toByteArray(Charsets.UTF_8).size
        if (pendingBytes + bytes > MAX_BATCH_BYTES) flush()
        pending[chunk.key] = chunk.text
        pendingBytes += bytes
        if (pending.size == MAX_BATCH_CHUNKS || pendingBytes == MAX_BATCH_BYTES) flush()
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val embedded = provider.embed(pending.values.toList())
        require(embedded.size == pending.size)
        require(embedded.all { it.size == identity.dimensions && it.all(Float::isFinite) })
        pending.keys.forEachIndexed { index, key -> vectors[key] = Quantized.quantize(embedded[index]) }
        this.embedded += pending.size
        pending.clear()
        pendingBytes = 0
    }

    private companion object {
        const val MAX_BATCH_CHUNKS = 16
        const val MAX_BATCH_BYTES = 256 * 1024
        const val MAX_CHUNK_BYTES = 32 * 1024
    }
}
