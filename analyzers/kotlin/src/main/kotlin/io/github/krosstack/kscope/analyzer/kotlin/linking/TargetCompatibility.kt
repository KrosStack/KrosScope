package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.analyzer.kotlin.planning.canSee
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId

/**
 * Whether two source sets are compiled for the same targets. A target belongs to one module, so source sets of
 * different modules are compared by the platforms of their targets. A source set without targets fits any.
 */
internal class TargetCompatibility(targets: List<Target>) {
    private val platformOf: Map<TargetId, Platform> = targets.associate { it.id to it.platform }

    fun shareTarget(first: SourceSet, second: SourceSet): Boolean = when {
        first.targets.isEmpty() || second.targets.isEmpty() -> true
        first.module == second.module -> second.targets.any { it in first.targets }
        else -> platformsFit(platformsOf(first), platformsOf(second))
    }

    private fun platformsFit(consumer: Set<Platform>, provider: Set<Platform>): Boolean =
        consumer.isEmpty() || provider.isEmpty() || consumer.any { platform -> provider.any { platform.canSee(it) } }

    private fun platformsOf(sourceSet: SourceSet): Set<Platform> = sourceSet.targets.mapNotNull(platformOf::get).toSet()
}
