package io.github.krosstack.kscope.benchmark.context.semantic

import io.github.krosstack.kscope.benchmark.context.ContextCheckpointRequest
import io.github.krosstack.kscope.benchmark.context.ContextTaskCheckpoints
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.TokenCount

internal class RecipeGuardedCheckpoints(
    private val checkpoints: ContextTaskCheckpoints,
    private val expected: ContentHash,
    private val current: () -> ContentHash = ContextPipelineIdentityFactory::recipe,
) : ContextTaskCheckpoints {
    override fun load(request: ContextCheckpointRequest): Map<TokenCount, TaskContextResult>? =
        checkpoints.load(request)

    override fun save(request: ContextCheckpointRequest, results: Map<TokenCount, TaskContextResult>) {
        checkRecipe()
        checkpoints.save(request, results)
    }

    fun checkRecipe() {
        check(current() == expected) { "Semantic ON recipe changed during the run." }
    }
}
