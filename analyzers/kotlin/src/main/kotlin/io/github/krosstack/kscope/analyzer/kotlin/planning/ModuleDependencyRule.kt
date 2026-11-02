package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId

/**
 * Which analysis modules of other Gradle modules a group depends on. A dependency edge applies to a source set when
 * it is scoped to that source set or to one it refines, or when it is not scoped. The target is the analysis module of
 * the same platform, else the default group (or `main`) for JVM and Android and then `commonMain`, which the JVM
 * target of a Kotlin Multiplatform module compiles when it has no source set of its own; for common code, `commonMain`.
 * A dependency that a module exposes through an `api` configuration is exposed to the groups that compile the source
 * set of that configuration: one that the selected target refines, or one without files that the platform of the group
 * compiles, as a common source set is compiled by each of its targets.
 */
internal class ModuleDependencyRule(
    private val layouts: Map<ModuleId, ModuleLayout>,
    private val edges: ModuleEdges,
    private val graph: GroupGraph,
) {
    private val analysed: Set<SourceSetId> = layouts.values.flatMap { it.groups }.flatMap { it.ids }.toSet()

    fun dependenciesOf(group: SourceSetGroup): List<SourceSetGroup> {
        val modules = group.members.flatMap(::applicableEdges).map { it.to }.distinct()
        return withExposed(modules, group.platform)
    }

    private fun withExposed(modules: List<ModuleId>, platform: Platform): List<SourceSetGroup> {
        val reached = LinkedHashSet(modules)
        val pending = ArrayDeque(modules)
        val targets = mutableListOf<SourceSetGroup>()
        while (pending.isNotEmpty()) {
            val module = pending.removeFirst()
            val selected = targetsIn(module, platform)
            targets += selected
            exposedBy(module, selected, platform).filter(reached::add).forEach(pending::addLast)
        }
        return targets.distinct()
    }

    private fun exposedBy(module: ModuleId, selected: List<SourceSetGroup>, platform: Platform): List<ModuleId> {
        val compiled = selected.flatMap { it.members }.flatMap { graph.ancestorsOf(it.id) + it.id }.toSet()
        return edges.from(module).filter { it.exposesTarget && isExposed(it, compiled, platform) }.map { it.to }
    }

    private fun isExposed(edge: ModuleEdge, compiled: Set<SourceSetId>, platform: Platform): Boolean =
        edge.scope == null || edge.scope in compiled || isCompiledWithoutFiles(edge, platform)

    private fun isCompiledWithoutFiles(edge: ModuleEdge, platform: Platform): Boolean {
        val compilers = edge.scopePlatforms
        val isCompiledFor = platform in compilers || (platform == Platform.ANDROID && Platform.JVM in compilers)
        return edge.scope !in analysed && isCompiledFor
    }

    private fun applicableEdges(sourceSet: SourceSet): List<ModuleEdge> {
        val scopes = graph.ancestorsOf(sourceSet.id) + sourceSet.id
        return edges.from(sourceSet.module).filter { it.scope == null || it.scope in scopes }
    }

    private fun targetsIn(module: ModuleId, platform: Platform): List<SourceSetGroup> {
        val layout = layouts[module] ?: return emptyList()
        return if (platform == Platform.COMMON) listOfNotNull(layout.commonMain()) else samePlatformIn(layout, platform)
    }

    private fun samePlatformIn(layout: ModuleLayout, platform: Platform): List<SourceSetGroup> {
        val samePlatform = layout.offered().filter { it.platform == platform }
        return samePlatform.mostRefined().ifEmpty { listOfNotNull(fallbackIn(layout, platform)) }
    }

    private fun fallbackIn(layout: ModuleLayout, platform: Platform): SourceSetGroup? =
        if (platform.isJvmLike()) layout.defaultOrMain() ?: layout.commonMain() else layout.commonMain()

    private fun List<SourceSetGroup>.mostRefined(): List<SourceSetGroup> =
        filter { group -> none { other -> graph.refines(other, group) } }
}

internal fun Platform.isJvmLike(): Boolean = this == Platform.JVM || this == Platform.ANDROID

/** Without dependency information a group may see every other offered module of a platform it can compile against. */
internal fun Platform.canSee(other: Platform): Boolean =
    other == this || other == Platform.COMMON || (isJvmLike() && other.isJvmLike())
