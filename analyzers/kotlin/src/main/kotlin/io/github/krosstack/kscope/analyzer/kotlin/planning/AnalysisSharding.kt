package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet

/**
 * Splits the modules whose facts are wanted over several requests that can run in parallel workers. Every request
 * asks for the facts of its own share only. In a build model without cycles a request holds its share and what the
 * share refers to, so each source set is analysed against the same modules as in one request for all. Cycles and
 * references to missing modules are settled by a worker from the modules it is given, in their order; a build model
 * that has them gives every request all modules, as one request for all would.
 */
internal class AnalysisSharding(private val minimumShardBytes: Long = MINIMUM_SHARD_BYTES) {
    fun split(plan: AnalysisPlan, weightOf: (WorkerModule) -> Long, limit: Int): List<List<WorkerModule>> {
        val requested = plan.modules.filter { module -> module.sourceSets.any { it.emitFactsFor } }
        val count = minOf(limit.toLong(), requested.size.toLong(), requested.sumOf(weightOf) / minimumShardBytes)
        if (count < 2) return listOf(plan.modules)
        val references = ModuleReferences(plan.modules).takeIf { it.areClosedAndAcyclic() }
        return balanced(requested, weightOf, count.toInt()).map { share -> requestFor(plan.modules, share, references) }
    }

    fun unitsPerShard(shards: List<List<WorkerModule>>, units: List<AnalysisUnit>): List<List<AnalysisUnit>> {
        val asked = shards.map { modules ->
            modules.flatMap { it.sourceSets }.filter { it.emitFactsFor }.map { it.sourceSet }.toSet()
        }
        val perShard = asked.map { ids -> units.filter { it.sourceSet?.id in ids } }
        val unassigned = units.filter { unit -> perShard.none { unit in it } }
        return perShard.mapIndexed { index, own -> if (index == 0) own + unassigned else own }
    }

    private fun balanced(
        requested: List<WorkerModule>,
        weightOf: (WorkerModule) -> Long,
        count: Int,
    ): List<List<WorkerModule>> {
        val shares = List(count) { mutableListOf<WorkerModule>() }
        val weights = LongArray(count)
        requested.sortedWith(compareByDescending<WorkerModule> { weightOf(it) }.thenBy { it.name }).forEach { module ->
            val lightest = weights.indices.minBy { weights[it] }
            shares[lightest] += module
            weights[lightest] += weightOf(module)
        }
        return shares.map { share -> share.sortedBy { it.name } }
    }

    private fun requestFor(
        modules: List<WorkerModule>,
        share: List<WorkerModule>,
        references: ModuleReferences?,
    ): List<WorkerModule> {
        val asked = share.map { it.name }.toSet()
        val included = references?.closureOf(asked) ?: modules.map { it.name }.toSet()
        return modules.filter { it.name in included }.map { module ->
            if (module.name in asked) module else module.copy(sourceSets = module.sourceSets.map(::withoutFacts))
        }
    }

    private fun withoutFacts(sourceSet: WorkerSourceSet) = sourceSet.copy(emitFactsFor = false)

    companion object {
        const val MINIMUM_SHARD_BYTES = 2L * 1024 * 1024
    }
}

private class ModuleReferences(modules: List<WorkerModule>) {
    private val referencesOf: Map<String, List<String>> =
        modules.associate { it.name to (it.dependsOn + it.dependencies + it.friends).distinct() }

    fun areClosedAndAcyclic(): Boolean = referencesOf.values.all { names -> names.all(referencesOf::containsKey) } &&
        !hasCycle()

    fun closureOf(roots: Set<String>): Set<String> {
        val reached = LinkedHashSet(roots)
        val pending = ArrayDeque(roots)
        while (pending.isNotEmpty()) {
            referencesOf.getValue(pending.removeFirst()).filter(reached::add).forEach(pending::addLast)
        }
        return reached
    }

    private fun hasCycle(): Boolean {
        val unresolved = referencesOf.mapValues { (_, names) -> names.toMutableSet() }.toMutableMap()
        while (true) {
            val free = unresolved.filterValues { it.isEmpty() }.keys
            if (free.isEmpty()) return unresolved.isNotEmpty()
            free.forEach(unresolved::remove)
            unresolved.values.forEach { it.removeAll(free) }
        }
    }
}
