package io.github.krosstack.kscope.semantic.chunk

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol

internal class ChunkGroups(private val text: ChunkText) {
    fun merge(chunks: List<Chunk>, symbols: List<Symbol>): List<Chunk> {
        val containers = symbols.associate { NodeRef.OfSymbol(it.id) to it.container }
        val small = chunks.filter { it.kind == ChunkKind.DECLARATION && text.small(it.text) }
        val groups = small.groupBy { it.artifact to containers[it.nodes.single()] }.values.flatMap { siblings ->
            partition(siblings).map { members ->
                if (members.size == 1) {
                    members.single()
                } else {
                    text.chunk(
                        ChunkKind.GROUP,
                        members.joinToString("\n\n") { it.text },
                        members.flatMap { it.nodes },
                        members.first().artifact,
                    )
                }
            }
        }
        val merged = small.toSet()
        return chunks.filterNot { it in merged } + groups
    }

    private fun partition(siblings: List<Chunk>): List<List<Chunk>> {
        val groups = mutableListOf<List<Chunk>>()
        var pending = emptyList<Chunk>()
        for (chunk in siblings) {
            val combined = (pending + chunk).joinToString("\n\n") { it.text }
            if (pending.size == MAX_MEMBERS || text.capped(combined) != combined) {
                groups += pending
                pending = emptyList()
            }
            pending += chunk
        }
        if (pending.isNotEmpty()) groups += pending
        return groups
    }

    private companion object {
        const val MAX_MEMBERS = 8
    }
}
