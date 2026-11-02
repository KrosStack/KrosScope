package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.incremental.SyntheticSourceCache
import io.github.krosstack.kscope.engine.incremental.UnitPlanner
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource

/**
 * Runs the surface synthesizers. A synthesizer whose units are all current is not run: its synthetic sources come
 * from the cache. When any unit is stale it runs over all its units, because it returns the sources of all of them,
 * and only the stale units are stored.
 */
internal class SynthesisStage(
    private val synthesizers: List<SurfaceSynthesizer>,
    private val planner: UnitPlanner,
    private val session: UpdateSession,
    private val runner: CapabilityRunner,
    private val cache: SyntheticSourceCache,
) {
    fun run(): List<SyntheticSource> =
        synthesizers.flatMap(::sourcesOf).sortedWith(compareBy({ it.sourceSet.value }, { it.virtualPath }))

    private fun sourcesOf(synthesizer: SurfaceSynthesizer): List<SyntheticSource> {
        val descriptor = synthesizer.descriptor
        val work = planner.plan(descriptor)
        val stale = work.map { it.unit }.filter { session.ledger.claimIfStale(it) }
        val cached = cache.lookup(descriptor.producer, work.map { it.unit })
        if (stale.isEmpty() && cached != null) return cached
        val job = CapabilityJob(descriptor.producer, work, stale)
        val sources = runner.run(job, emptyList()) { request, sink -> synthesizer.synthesize(request, sink) }
        cache.remember(descriptor.producer, work.map { it.unit }, sources)
        return sources
    }
}
