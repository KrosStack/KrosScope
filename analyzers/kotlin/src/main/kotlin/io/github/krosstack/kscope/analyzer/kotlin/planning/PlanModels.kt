package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule

/**
 * Everything the planner needs. [files] holds the Kotlin and Java files of every source set, also of those that are
 * only analysis context, and [requested] the source sets whose facts are wanted.
 */
internal class PlanInput(
    val model: BuildModel,
    val files: Map<SourceSetId, List<WorkerFile>>,
    val syntheticSources: List<SyntheticSource>,
    val classpaths: Map<SourceSetId, List<String>>,
    val requested: Set<SourceSetId>,
)

/**
 * [dependenciesAssumed] is true when the build model said nothing about dependencies and they were guessed. The
 * assumption is announced by one source set only, the first in id order, so that a scan reports it once. [reach]
 * tells which modules a module sees only through heuristic dependencies.
 */
internal class AnalysisPlan(
    val modules: List<WorkerModule>,
    val dependenciesAssumed: Boolean,
    val reach: HeuristicReach,
) {
    val fingerprints: Map<SourceSetId, Fingerprint> =
        modules.flatMap { it.sourceSets }.associate { it.sourceSet to it.fingerprint }
    val assumptionAnnouncer: SourceSetId? = fingerprints.keys.minByOrNull { it.value }.takeIf { dependenciesAssumed }
}
