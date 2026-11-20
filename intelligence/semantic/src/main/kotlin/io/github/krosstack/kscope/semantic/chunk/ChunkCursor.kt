package io.github.krosstack.kscope.semantic.chunk

public class ChunkCursor internal constructor(private val chunks: Iterator<Chunk>) {
    /**
     * True once a call to [next] found no further chunk. The cursor never reads ahead, because each chunk may cost a
     * round trip to the embedding provider, so the call that sets it returns the last chunks or none.
     */
    public var complete: Boolean = false
        private set

    /** [maxBytes] must accommodate one maximum-sized chunk (32 KiB); unused capacity avoids reading ahead. */
    public fun next(maxChunks: Int, maxBytes: Int): List<Chunk> {
        require(maxChunks > 0 && maxBytes >= MAX_CHUNK_BYTES)
        val batch = mutableListOf<Chunk>()
        var bytes = 0
        while (!complete && batch.size < maxChunks && maxBytes - bytes >= MAX_CHUNK_BYTES) {
            if (!chunks.hasNext()) {
                complete = true
            } else {
                val chunk = chunks.next()
                bytes += chunk.text.toByteArray(Charsets.UTF_8).size
                batch += chunk
            }
        }
        return batch
    }

    private companion object {
        const val MAX_CHUNK_BYTES = 32 * 1024
    }
}
