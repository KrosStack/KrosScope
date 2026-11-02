package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.CoverageSummary
import io.github.krosstack.kscope.benchmark.score.Score
import io.github.krosstack.kscope.benchmark.score.build.BuildModelScore
import io.github.krosstack.kscope.benchmark.score.code.FileDeclarationScore
import io.github.krosstack.kscope.benchmark.score.code.FileEdgeScore
import kotlinx.serialization.Serializable

/** What a scan cost: wall time per stage of the engine and in total, and what it produced. [totalMillis] varies. */
@Serializable
public data class ScanMetrics(
    public val artifacts: Int,
    public val unitsProduced: Int,
    public val stageMillis: Map<String, Long>,
    public val totalMillis: Long,
    public val errors: Int,
    public val warnings: Int,
    public val infos: Int,
)

@Serializable
public data class DeclarationMetrics(public val overall: Score, public val files: List<FileDeclarationScore>)

/** Which closed-world files a gate covers. */
public enum class FileSelection {
    /** Kotlin/JVM and common source sets, which compile without the Android classpath. */
    WITHOUT_ANDROID,
    ALL,
}

@Serializable
public data class ClosedWorldMetrics(public val files: List<FileEdgeScore>) {
    public fun overall(selection: FileSelection): Score = Score.combine(select(selection).map { it.score })

    public fun deferredCount(selection: FileSelection): Int = select(selection).sumOf { it.deferred.size }

    public fun select(selection: FileSelection): List<FileEdgeScore> = when (selection) {
        FileSelection.WITHOUT_ANDROID -> files.filterNot { it.androidDependent }
        FileSelection.ALL -> files
    }
}

/** Everything one conformance run measured, for one scan mode and the level it stands for. */
@Serializable
public data class ConformanceResult(
    public val mode: String,
    public val level: String,
    public val scan: ScanMetrics,
    public val coverage: CoverageSummary,
    public val buildModel: BuildModelScore,
    public val declarations: DeclarationMetrics,
    public val closedWorld: ClosedWorldMetrics,
    public val relationshipClasses: List<ClassScore>,
    public val goldProblems: List<String>,
)
