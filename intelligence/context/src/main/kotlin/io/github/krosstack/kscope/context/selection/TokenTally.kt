package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.context.token.BpeBlockBoundary

internal class TokenTally(private val additive: BlockAdditive, task: String) {
    private val taskBlock = ContextMarkdown.taskBlock(task)
    private val taskMeasure = taskBlock?.let(additive::measure) ?: 0L
    private val unresolvedMeasures = HashMap<Int, Long>()

    val taskBlockIsValid: Boolean = taskBlock == null || BpeBlockBoundary.valid(taskBlock)

    fun tokens(fileMeasure: Long, unresolvedCount: Int): Int =
        additive.tokens(taskMeasure + fileMeasure + unresolvedMeasure(unresolvedCount))

    private fun unresolvedMeasure(count: Int): Long = unresolvedMeasures[count] ?: measureUnresolved(count)

    private fun measureUnresolved(count: Int): Long {
        val block = ContextMarkdown.unresolvedBlock(count) ?: return 0L
        return additive.measure(block).also { unresolvedMeasures[count] = it }
    }
}
