package io.github.krosstack.kscope.semantic.chunk

internal class ChunkGroups(private val text: ChunkText) {
    fun merge(siblings: Sequence<Chunk>): Sequence<Chunk> = partition(siblings).map { members ->
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

    private fun partition(siblings: Sequence<Chunk>): Sequence<List<Chunk>> = sequence {
        var pending = emptyList<Chunk>()
        for (chunk in siblings) {
            if (chunk.kind != ChunkKind.DECLARATION || !text.small(chunk.text)) {
                yield(listOf(chunk))
                continue
            }
            val combined = (pending + chunk).joinToString("\n\n") { it.text }
            if (pending.isNotEmpty() && text.capped(combined) != combined) {
                yield(pending)
                pending = emptyList()
            }
            pending += chunk
            if (pending.size == MAX_MEMBERS) {
                yield(pending)
                pending = emptyList()
            }
        }
        if (pending.isNotEmpty()) yield(pending)
    }

    private companion object {
        const val MAX_MEMBERS = 8
    }
}
