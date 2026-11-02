package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisSessions
import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client may share the source sets of a scan over several workers, each given its share and the modules the share
 * refers to. That is sound only if a source set is analysed to the same facts whichever other modules are present.
 */
class ShardedAnalysisTest {
    private val modules = ReferenceWorkspaceLayout.modules()

    @Test
    fun `a request with a share of the modules and what they refer to gives the facts of the request for all`() {
        val everything = analyzeAll(modules).associateBy { it.owner.key }
        val shares = modules.chunked(modules.size / SHARES + 1)
        assertTrue(shares.size >= SHARES)

        for (share in shares) {
            val request = requestFor(share)
            assertTrue(request.size < modules.size || share.size == modules.size, "the share brings no context")
            val sharded = AnalysisSessions.open(request).use { FactPass(WorkerIdentity.producer).batchesOf(it) }

            val asked = share.map { it.sourceSets.single().sourceSet.value }
            assertEquals(asked.sorted(), sharded.map { it.owner.key }.sorted())
            sharded.forEach { batch ->
                assertEquals(json(everything.getValue(batch.owner.key)), json(batch), batch.owner.key)
            }
        }
    }

    private fun requestFor(share: List<WorkerModule>): List<WorkerModule> {
        val asked = share.map { it.name }.toSet()
        val included = closureOf(asked)
        return modules.filter { it.name in included }.map { module ->
            if (module.name in asked) module else module.copy(sourceSets = module.sourceSets.map(::withoutFacts))
        }
    }

    private fun withoutFacts(sourceSet: WorkerSourceSet) = sourceSet.copy(emitFactsFor = false)

    private fun closureOf(names: Set<String>): Set<String> {
        val byName = modules.associateBy { it.name }
        val reached = LinkedHashSet(names)
        val pending = ArrayDeque(names)
        while (pending.isNotEmpty()) {
            val module = byName.getValue(pending.removeFirst())
            (module.dependsOn + module.dependencies + module.friends).filter(reached::add).forEach(pending::addLast)
        }
        return reached
    }

    private fun json(batch: FactBatch): String = CanonicalFacts.toJson(listOf(batch))

    private companion object {
        const val SHARES = 3
    }
}
