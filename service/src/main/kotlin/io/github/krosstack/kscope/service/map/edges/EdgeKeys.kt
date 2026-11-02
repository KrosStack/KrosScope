package io.github.krosstack.kscope.service.map.edges

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RelationKind

/** Packs the ends, the kind and the certainty of an edge into one number that sorts in that order. */
internal object EdgeKeys {
    private const val FROM_SHIFT = 36
    private const val TO_SHIFT = 8
    private const val KIND_SHIFT = 2
    private const val NODE_MASK = 0xFFFFFFFL
    private const val KIND_MASK = 0xFL
    private const val CERTAINTY_MASK = 0x3L
    const val STRIDE = 5

    val KINDS: List<RelationKind> = RelationKind.entries.filter { it != RelationKind.CONTAINS }
    val CERTAINTIES: List<Certainty> = Certainty.entries
    private val kindIndexes = IntArray(RelationKind.entries.size) { KINDS.indexOf(RelationKind.entries[it]) }

    fun pack(from: Int, to: Int, kind: RelationKind, certainty: Certainty): Long =
        (from.toLong() shl FROM_SHIFT) or (to.toLong() shl TO_SHIFT) or
            (kindIndexes[kind.ordinal].toLong() shl KIND_SHIFT) or certainty.ordinal.toLong()

    /** Five numbers per distinct key of [sorted]: the ends, the kind index, the certainty index and the count. */
    fun flatten(sorted: LongArray): IntArray {
        val flat = IntArray(distinct(sorted) * STRIDE)
        var written = 0
        var position = 0
        while (position < sorted.size) {
            val key = sorted[position]
            var end = position
            while (end < sorted.size && sorted[end] == key) end++
            flat[written++] = (key ushr FROM_SHIFT).toInt()
            flat[written++] = ((key ushr TO_SHIFT) and NODE_MASK).toInt()
            flat[written++] = ((key ushr KIND_SHIFT) and KIND_MASK).toInt()
            flat[written++] = (key and CERTAINTY_MASK).toInt()
            flat[written++] = end - position
            position = end
        }
        return flat
    }

    private fun distinct(sorted: LongArray): Int = sorted.indices.count { it == 0 || sorted[it] != sorted[it - 1] }
}
