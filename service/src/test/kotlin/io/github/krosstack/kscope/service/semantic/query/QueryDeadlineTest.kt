package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.RecordingEmbedding
import io.github.krosstack.kscope.service.semantic.WorkerDeadlines
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class QueryDeadlineTest {
    @Test
    fun `admission startup and exchange spend the same allowance`() {
        val clock = TestTimeSource()
        val deadline = QueryDeadline(clock)
        assertEquals(10.seconds, deadline.remaining())
        clock += 2.seconds
        assertEquals(8.seconds, deadline.remaining())
        clock += 3.seconds
        assertEquals(5.seconds, deadline.remaining())
        clock += 4.seconds
        assertEquals(1.seconds, deadline.remaining())
        assertFalse(deadline.expired())
        clock += 1.seconds
        assertTrue(deadline.expired())
        assertEquals(0.seconds, deadline.remaining())
        clock += 10.seconds
        assertEquals(0.seconds, deadline.remaining())
    }

    @Test
    fun `index startup keeps its separate 120 second allowance`() {
        val clock = TestTimeSource()
        val query = QueryDeadline(clock)
        val deadlines = WorkerDeadlines(absoluteQuery = query)
        clock += 119.seconds
        assertTrue(query.expired())
        assertEquals(120.seconds, deadlines.startup)
        assertEquals(120.seconds, deadlines.batch)
        assertEquals(10.seconds, deadlines.query)
    }

    @Test
    fun `expired work cannot start another exchange`() {
        val clock = TestTimeSource()
        val deadline = QueryDeadline(clock)
        val provider = RecordingEmbedding()
        var starts = 0
        LazyWorkerProvider(provider.identity, provider.id) { _, _ ->
            starts++
            LazyWorkerProvider.Started(provider, { null }, { _, _, _, _ -> error("Expired exchange started") }) {}
        }.use { lazy ->
            clock += 10.seconds
            val result = assertIs<WorkerEmbeddingProvider.QueryResult.Refused>(lazy.embedQuery("text", deadline) {})
            assertEquals(WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED, result.reason)
            assertEquals(0, starts)
            assertEquals("text", lazy.cap("text", 4))
            assertEquals(1, starts)
        }
    }
}
