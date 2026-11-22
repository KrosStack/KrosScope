package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.model.context.ContextInput
import kotlin.math.nextDown

internal object StructuralContextTail {
    fun input(head: ContextInput, baseline: ContextInput, floor: Double?): ContextInput {
        val positive = baseline.candidates.filter { it.relevance.isFinite() && it.relevance > 0.0 }
        val peak = positive.maxOfOrNull { it.relevance }
        val limit = floor?.takeIf { it.isFinite() && it > 0.0 }
        if (limit == null || peak == null) return head
        val present = head.candidates.map { it.node }.toSet()
        val scale = limit / peak
        val ceiling = limit.nextDown()
        val tail = positive.filter { it.node !in present }
            .map { it.copy(relevance = minOf(it.relevance * scale, ceiling)) }
            .filter { it.relevance > 0.0 }
        val added = tail.map { it.node }.toSet()
        return head.copy(
            candidates = head.candidates + tail,
            tests = (head.tests + baseline.tests.filter { it in added }).distinct(),
        )
    }
}
