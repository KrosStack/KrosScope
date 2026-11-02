package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.ArtifactRef

internal class FileRanks private constructor(private val ranks: Map<ArtifactRef, Int>) {
    fun rankOf(file: ArtifactRef): Int = ranks.getValue(file)

    companion object {
        /** Null when [order] calls two of the [files] equal: their ranks would be a guess. */
        fun of(files: Collection<ArtifactRef>, order: Comparator<ArtifactRef>): FileRanks? {
            val sorted = files.distinct().sortedWith(order)
            val tied = sorted.zipWithNext(order::compare).any { it == 0 }
            return if (tied) null else FileRanks(sorted.withIndex().associate { (rank, file) -> file to rank })
        }
    }
}
