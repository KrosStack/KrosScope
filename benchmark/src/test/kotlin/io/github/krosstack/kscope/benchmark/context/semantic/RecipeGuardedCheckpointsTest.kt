package io.github.krosstack.kscope.benchmark.context.semantic

import io.github.krosstack.kscope.benchmark.context.ContextCheckpointRequest
import io.github.krosstack.kscope.benchmark.context.ContextTaskCheckpoints
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecipeGuardedCheckpointsTest {
    private val request = ContextCheckpointRequest(BenchmarkSet.ORIGINAL, "task", "fingerprint")

    @Test
    fun `a recipe change during the run refuses to save and saves nothing`() {
        val delegate = Recording()
        var recipe = ContentHash("before")
        val guarded = RecipeGuardedCheckpoints(delegate, ContentHash("before")) { recipe }
        guarded.save(request, emptyMap())
        recipe = ContentHash("after")
        assertFailsWith<IllegalStateException> { guarded.save(request, emptyMap()) }
        assertFailsWith<IllegalStateException> { guarded.checkRecipe() }
        assertEquals(1, delegate.saved.size)
    }

    @Test
    fun `an unchanged recipe saves and loads through the delegate`() {
        val delegate = Recording()
        val guarded = RecipeGuardedCheckpoints(delegate, ContentHash("same")) { ContentHash("same") }
        guarded.save(request, emptyMap())
        guarded.checkRecipe()
        assertEquals(listOf(request), delegate.saved)
        assertTrue(guarded.load(request).orEmpty().isEmpty())
        assertEquals(listOf(request), delegate.loaded)
    }

    private class Recording : ContextTaskCheckpoints {
        val saved = mutableListOf<ContextCheckpointRequest>()
        val loaded = mutableListOf<ContextCheckpointRequest>()

        override fun load(request: ContextCheckpointRequest): Map<TokenCount, TaskContextResult>? {
            loaded += request
            return null
        }

        override fun save(request: ContextCheckpointRequest, results: Map<TokenCount, TaskContextResult>) {
            saved += request
        }
    }
}
