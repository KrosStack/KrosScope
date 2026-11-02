package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.model.context.TokenCount
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextTaskCheckpointsTest {
    @Test
    fun `completed task checkpoints round trip and reject a different freeze or task fingerprint`() {
        val directory = Files.createTempDirectory("kscope-context-checkpoints")
        try {
            val store = FileContextTaskCheckpoints(directory, "freeze-one")
            val request = ContextCheckpointRequest(BenchmarkSet.ORIGINAL, "task", "source-and-options")
            val result = TaskContextResult("task", "statement", ContextGoldSpec(listOf("source")), emptyMap())
            val results = linkedMapOf(TokenCount(4_000) to result, TokenCount(2_000) to result)

            assertNull(store.load(request))
            store.save(request, results)
            assertEquals(results, store.load(request))
            assertNull(store.load(request.copy(fingerprint = "different-source")))
            assertNull(FileContextTaskCheckpoints(directory, "freeze-two").load(request))
            assertNull(FileContextTaskCheckpoints(directory, "freeze-one", resume = false).load(request))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `progress event data contains only identifiers and boundaries`() {
        val events = mutableListOf<ContextProgressEvent>()
        val trace = ContextProgressTrace(BenchmarkSet.HELD_OUT, "task-id", events::add)
        trace.emit(ContextProgressBoundary.METHOD_STARTED, TokenCount(2_000), ContextBenchmarkMethod.FULL_COMPILER)

        assertEquals(
            listOf(
                ContextProgressEvent(
                    ContextProgressBoundary.METHOD_STARTED,
                    BenchmarkSet.HELD_OUT,
                    "task-id",
                    TokenCount(2_000),
                    ContextBenchmarkMethod.FULL_COMPILER,
                ),
            ),
            events,
        )
    }
}
