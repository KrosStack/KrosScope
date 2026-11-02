package io.github.krosstack.kscope.benchmark.run

import kotlinx.serialization.Serializable

/** What one level measured: scan time, the headline scores of its gate files and whether its gate was met. */
@Serializable
public data class LevelSummary(
    public val level: String,
    public val totalMillis: Long,
    public val stageMillis: Map<String, Long>,
    public val declarationPrecision: Double,
    public val declarationRecall: Double,
    public val edgePrecision: Double,
    public val edgeRecall: Double,
    public val relationships: Int,
    public val compilerRelationships: Int,
    public val gateMet: Boolean,
    public val gateFailures: List<String>,
)

/** The agreement of one relationship class at one level; the ratios are null while the class has no score. */
@Serializable
public data class ClassAtLevel(
    public val level: String,
    public val precision: Double?,
    public val recall: Double?,
    public val missing: Int,
    public val extra: Int,
    public val evidenceDiffers: Int,
)

@Serializable
public data class ClassAcrossLevels(
    public val name: String,
    public val goldCount: Int,
    public val levels: List<ClassAtLevel>,
)

@Serializable
public data class LevelComparisonData(
    public val levels: List<LevelSummary>,
    public val classes: List<ClassAcrossLevels>,
)
