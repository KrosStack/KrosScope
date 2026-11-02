package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal object UnicodePrefix {
    fun truncate(text: String, budget: TokenCount, estimator: TokenEstimator): String {
        if (estimator.count(text).value <= budget.value) return text
        var low = 0
        var high = text.codePointCount(0, text.length)
        while (low < high) {
            val middle = low + (high - low + 1) / 2
            val prefix = text.substring(0, text.offsetByCodePoints(0, middle))
            if (estimator.count(prefix).value <= budget.value) low = middle else high = middle - 1
        }
        return text.substring(0, text.offsetByCodePoints(0, low))
    }
}
