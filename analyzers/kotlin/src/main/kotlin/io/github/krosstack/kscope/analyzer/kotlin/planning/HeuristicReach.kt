package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSetId

/**
 * Which modules a source set sees only through dependencies that the build model is not sure of: modules that its
 * analysis module is wired to, but would not be wired to if only the dependencies that are not heuristic counted. The
 * wiring follows the same rules in both cases, so a module that an exact dependency reaches only through another
 * module's `implementation` dependency, which the compiler does not see, does not count as reached for sure.
 */
internal class HeuristicReach(private val bySourceSet: Map<SourceSetId, Set<ModuleId>>) {
    fun reachesOnlyHeuristically(sourceSet: SourceSetId, module: ModuleId): Boolean =
        module in bySourceSet[sourceSet].orEmpty()
}
