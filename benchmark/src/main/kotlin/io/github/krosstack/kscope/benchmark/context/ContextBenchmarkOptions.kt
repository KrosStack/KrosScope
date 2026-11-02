package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

public class ContextBenchmarkOptions(
    public val budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
    public val methods: List<ContextBenchmarkMethod> = DEFAULT_METHODS,
    public val estimator: TokenEstimator = TokenEstimators.create(),
    public val progress: (ContextProgressEvent) -> Unit = {},
    public val checkpoints: ContextTaskCheckpoints? = null,
) {
    init {
        require(budgets.isNotEmpty() && budgets.all { it.value > 0 }) { "Benchmark budgets must be positive." }
        require(budgets.distinct().size == budgets.size) { "Benchmark budgets must be unique." }
        require(methods.isNotEmpty() && methods.distinct().size == methods.size) { "Benchmark methods must be unique." }
    }

    public companion object {
        public val DEFAULT_METHODS: List<ContextBenchmarkMethod> = listOf(
            ContextBenchmarkMethod.FULL_COMPILER,
            ContextBenchmarkMethod.LEXICAL_COMPILER,
            ContextBenchmarkMethod.BM25_RAW,
            ContextBenchmarkMethod.BM25_ENGLISH,
            ContextBenchmarkMethod.PATH_ORDER,
        )
    }
}

public object ContextBenchmarkBudgets {
    public val values: List<TokenCount> = listOf(
        TokenCount(TWO),
        TokenCount(FOUR),
        TokenCount(EIGHT),
        TokenCount(SIXTEEN),
    )
    public fun parse(csv: String): List<TokenCount>? {
        val numbers = csv.split(',').map { it.toIntOrNull() }
        val known = values.map { it.value }.toSet()
        val valid = numbers.none { it == null } && numbers.toSet().size == numbers.size && known.containsAll(numbers)
        return if (valid) numbers.filterNotNull().sorted().map(::TokenCount) else null
    }

    private const val TWO = 2_000
    private const val FOUR = 4_000
    private const val EIGHT = 8_000
    private const val SIXTEEN = 16_000
}
