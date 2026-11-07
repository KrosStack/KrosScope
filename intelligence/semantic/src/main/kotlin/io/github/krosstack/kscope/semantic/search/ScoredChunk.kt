package io.github.krosstack.kscope.semantic.search

import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.store.Quantized

public data class ScoredChunk(public val chunk: Chunk, public val vector: Quantized)
