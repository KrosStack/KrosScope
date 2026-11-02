package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.store.codec.resolution
import io.github.krosstack.kscope.store.read.Resolutions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class InsertPerformanceTest {
    @Test
    fun `a batch of fifty thousand relationships is stored well within five seconds`() {
        val batch = Builders.edgeBatch("big-unit", relationships(RELATIONSHIPS))
        TempDirectory().use { directory ->
            openedStore(directory.path.resolve("graph.db")).use { store ->
                val elapsed = measureTime { store.replace(batch) }

                assertTrue(elapsed.inWholeMilliseconds < LIMIT_MILLIS, "Storing took $elapsed")
                assertEquals(RELATIONSHIPS, store.relationships(RelationshipQuery()).size)
                assertEquals(SITES, store.outgoing(Builders.symbolRef("source-0")).size)
            }
        }
    }

    private fun relationships(count: Int): List<Relationship> = List(count) { index ->
        Samples.relationship.copy(
            from = Builders.symbolRef("source-${index / SITES}"),
            kind = RelationKind.entries[index % RelationKind.entries.size],
            resolution = resolution(index),
            attributes = mapOf("index" to index.toString()),
        )
    }

    private fun resolution(index: Int) = if (index % UNRESOLVED_EVERY == 0) {
        Resolutions.unresolved("hint $index")
    } else {
        Resolutions.resolved("target-${index % TARGETS}")
    }

    private companion object {
        const val RELATIONSHIPS = 50_000
        const val SITES = 5
        const val TARGETS = 500
        const val UNRESOLVED_EVERY = 10
        const val LIMIT_MILLIS = 5_000
    }
}
