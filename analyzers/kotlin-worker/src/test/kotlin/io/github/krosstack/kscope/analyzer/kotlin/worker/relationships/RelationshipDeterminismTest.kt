package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.model.CanonicalFacts
import kotlin.test.Test
import kotlin.test.assertEquals

class RelationshipDeterminismTest {
    private val modules = RelationSamples.multiplatform + listOf(RelationSamples.mixed)

    @Test
    fun `analysing the same input twice gives identical bytes`() {
        assertEquals(CanonicalFacts.toJson(analyzeAll(modules)), CanonicalFacts.toJson(analyzeAll(modules)))
    }

    @Test
    fun `the order of modules in the request does not change the output`() {
        assertEquals(
            CanonicalFacts.toJson(analyzeAll(modules)),
            CanonicalFacts.toJson(analyzeAll(modules.reversed())),
        )
    }
}
