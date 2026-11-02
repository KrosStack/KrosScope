package io.github.krosstack.kscope.benchmark.stats

import kotlinx.serialization.Serializable

@Serializable
public enum class ComparisonMetric {
    RECALL_5,
    RECALL_10,
    NDCG_10,
    GOLD_CONTEXT_RECALL,
    TOKEN_PRECISION,
}

@Serializable
public enum class ComparisonOutcome { SIGNIFICANT_WIN, SIGNIFICANT_LOSS, INCONCLUSIVE, UNMEASURED }

@Serializable
public data class PairedValue(public val taskId: String, public val candidate: Double, public val baseline: Double)

@Serializable
public data class BootstrapSettings(
    public val seed: Long = BootstrapPolicy.SEED,
    public val resamples: Int = BootstrapPolicy.RESAMPLES,
    public val confidenceLevel: Double = BootstrapPolicy.CONFIDENCE_LEVEL,
    public val percentileInterpolation: PercentileInterpolation = PercentileInterpolation.LINEAR,
)

@Serializable
public enum class PercentileInterpolation { LINEAR, }

public object BootstrapPolicy {
    public const val SEED: Long = 20261005L
    public const val RESAMPLES: Int = 10_000
    public const val CONFIDENCE_LEVEL: Double = 0.95
    internal const val LOWER_PERCENTILE = 0.025
    internal const val UPPER_PERCENTILE = 0.975
}

@Serializable
public data class ComparisonReport(
    public val metric: ComparisonMetric,
    public val candidate: String,
    public val baseline: String,
    public val candidateMean: Double?,
    public val baselineMean: Double?,
    public val pointDelta: Double?,
    public val lower95: Double?,
    public val upper95: Double?,
    public val significant: Boolean,
    public val outcome: ComparisonOutcome,
    public val pairs: List<PairedValue>,
    public val settings: BootstrapSettings,
    public val valid: Boolean,
)
