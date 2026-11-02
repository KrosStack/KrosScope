package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class LineCuts(private val fragment: ContextFragment) {
    private val ends = fragment.text.indices.filter { fragment.text[it] == '\n' }
    val count: Int get() = ends.size

    fun prefix(lines: Int, estimator: TokenEstimator): ContextFragment {
        val text = fragment.text.substring(0, if (lines == 0) 0 else ends[lines - 1])
        return fragment.copy(range = range(lines), text = text, tokens = estimator.count(text))
    }

    private fun range(lines: Int): TextRange? = fragment.range?.let { whole ->
        if (lines == 0) return@let whole
        val lineStart = if (lines == 1) 0 else ends[lines - 2] + 1
        val leading = if (lines == 1) whole.startColumn else 1
        val endColumn = leading + ends[lines - 1] - lineStart
        TextRange(whole.startLine, whole.startColumn, whole.startLine + lines - 1, endColumn)
    }
}
