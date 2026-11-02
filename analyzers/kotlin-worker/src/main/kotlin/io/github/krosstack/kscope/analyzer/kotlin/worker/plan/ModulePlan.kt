package io.github.krosstack.kscope.analyzer.kotlin.worker.plan

import io.github.krosstack.kscope.spi.worker.WorkerModule

internal enum class PlanProblemKind(val code: String) {
    UNKNOWN_MODULE("kotlin.unknown-module-reference"),
    CYCLE("kotlin.cyclic-module-reference"),
}

internal data class PlanProblem(val module: String, val kind: PlanProblemKind, val referenced: String)

/** A module with every edge resolved to modules that come earlier in [ModulePlan.ordered]. */
internal data class PlannedModule(
    val module: WorkerModule,
    val dependsOn: List<String>,
    val regularDependencies: List<String>,
    val friends: List<String>,
)

/**
 * Orders the modules of a request so every dependency precedes its dependents, and expands each regular dependency
 * and friend with its `dependsOn` closure because Analysis API visibility is not transitive across modules.
 * References to unknown modules and edges that would close a cycle are dropped and reported.
 */
internal class ModulePlan(val ordered: List<PlannedModule>, val problems: List<PlanProblem>) {
    companion object {
        fun of(modules: List<WorkerModule>): ModulePlan = PlanBuilder(modules).build()
    }
}

private class PlanBuilder(modules: List<WorkerModule>) {
    private val byName: Map<String, WorkerModule> = modules.associateBy { it.name }
    private val names: List<String> = modules.map { it.name }.distinct()
    private val problems = mutableListOf<PlanProblem>()

    fun build(): ModulePlan {
        val wanted = names.associateWith(::wantedEdges)
        val order = topologicalOrder(wanted)
        val position = order.withIndex().associate { it.value to it.index }
        val ordered = order.map { name ->
            val edges = wanted.getValue(name)
            PlannedModule(
                byName.getValue(name),
                edges.dependsOn.earlier(name, position),
                edges.regular.earlier(name, position),
                edges.friends.earlier(name, position),
            )
        }
        return ModulePlan(ordered, problems.distinct())
    }

    private fun wantedEdges(name: String): Edges {
        val module = byName.getValue(name)
        val dependsOn = known(module, module.dependsOn)
        val regular = known(module, module.dependencies).flatMap(::withDependsOnClosure).distinct()
        val friends = known(module, module.friends).flatMap(::withDependsOnClosure).distinct()
        return Edges(dependsOn, regular.filterNot { it == name }, friends.filterNot { it == name })
    }

    private fun known(module: WorkerModule, references: List<String>): List<String> =
        references.distinct().filter { reference ->
            (reference in byName).also {
                if (!it) problems += PlanProblem(module.name, PlanProblemKind.UNKNOWN_MODULE, reference)
            }
        }

    private fun withDependsOnClosure(name: String): List<String> {
        val seen = linkedSetOf<String>()
        val pending = ArrayDeque(listOf(name))
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (seen.add(next)) pending.addAll(byName.getValue(next).dependsOn.filter { it in byName })
        }
        return seen.toList()
    }

    private fun topologicalOrder(wanted: Map<String, Edges>): List<String> {
        val order = mutableListOf<String>()
        val state = mutableMapOf<String, Boolean>()
        fun visit(name: String) {
            if (state[name] != null) return
            state[name] = false
            wanted.getValue(name).all().forEach { dependency ->
                if (state[dependency] == false) {
                    problems += PlanProblem(name, PlanProblemKind.CYCLE, dependency)
                } else {
                    visit(dependency)
                }
            }
            state[name] = true
            order += name
        }
        names.forEach(::visit)
        return order
    }

    private fun List<String>.earlier(owner: String, position: Map<String, Int>): List<String> =
        filter { position.getValue(it) < position.getValue(owner) }
}

private class Edges(val dependsOn: List<String>, val regular: List<String>, val friends: List<String>) {
    fun all(): List<String> = (dependsOn + regular + friends).distinct()
}
