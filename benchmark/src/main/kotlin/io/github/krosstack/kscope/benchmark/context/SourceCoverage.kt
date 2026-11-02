package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.ArtifactRef

internal class SourceCoverage(spans: List<SourceSpan>) {
    val byFile: Map<ArtifactRef, List<SourceInterval>> = spans.filter { it.interval.start < it.interval.end }
        .groupBy({ it.artifact }, { it.interval }).mapValues { (_, intervals) -> merge(intervals) }

    fun contains(span: SourceSpan): Boolean = byFile[span.artifact].orEmpty().any {
        it.start <= span.interval.start && it.end >= span.interval.end
    }

    private fun merge(intervals: List<SourceInterval>): List<SourceInterval> {
        val merged = mutableListOf<SourceInterval>()
        intervals.sortedWith(compareBy<SourceInterval> { it.start }.thenBy { it.end }).forEach { next ->
            val previous = merged.lastOrNull()
            if (previous == null || next.start > previous.end) {
                merged.add(next)
            } else {
                merged[merged.lastIndex] = SourceInterval(previous.start, maxOf(previous.end, next.end))
            }
        }
        return merged
    }
}
