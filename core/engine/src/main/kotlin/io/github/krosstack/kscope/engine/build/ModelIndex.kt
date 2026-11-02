package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel

/** Lookups over a build model: containment by directory, source sets per module and the dependency scope. */
internal class ModelIndex(val model: BuildModel) {
    private val sourceSetsById = model.sourceSets.associateBy { it.id }
    private val sourceSetsByModule = model.sourceSets.groupBy { it.module }
    private val modulesById = model.modules.associateBy { it.id }
    private val buildRootsById = model.buildRoots.associateBy { it.id }
    private val moduleDirectories: Map<RepositoryId?, DirectoryIndex<Module>> = model.modules
        .groupBy { buildRootsById[it.buildRoot]?.repository }
        .mapValues { (_, modules) ->
            DirectoryIndex(modules.groupBy { it.directory.value }.mapValues { smallestId(it.value) })
        }
    private val buildRootDirectories: Map<RepositoryId, DirectoryIndex<BuildRoot>> = model.buildRoots
        .groupBy { it.repository }
        .mapValues { (_, roots) -> DirectoryIndex(roots.associateBy { it.directory.value }) }
    private val dependencyEdges = model.dependencies.filter { it.kind == RelationKind.DEPENDS_ON }
    private val declaredDependencies: Map<ModuleId, List<ModuleDependency>> = dependencyEdges
        .mapNotNull(::moduleDependency).distinct()
        .sortedWith(compareBy({ it.to.value }, { it.sourceSet?.value }, { it.configuration }))
        .groupBy { it.from }
    private val moduleDependencies: Map<ModuleId, Set<ModuleId>> =
        declaredDependencies.mapValues { (_, edges) -> edges.map { it.to }.toSet() }

    fun sourceSet(id: SourceSetId): SourceSet? = sourceSetsById[id]

    fun module(id: ModuleId): Module? = modulesById[id]

    fun sourceSetsOf(module: ModuleId): List<SourceSet> = sourceSetsByModule[module].orEmpty().sortedBy { it.id.value }

    fun moduleContaining(ref: ArtifactRef): Module? =
        moduleDirectories[ref.repository]?.nearest(ref.path.value) ?: moduleDirectories[null]?.nearest(ref.path.value)

    fun buildRootContaining(ref: ArtifactRef): BuildRoot? =
        buildRootDirectories[ref.repository]?.nearest(ref.path.value)

    val hasDependencyEdges: Boolean get() = dependencyEdges.isNotEmpty()

    /** The module and everything it depends on transitively; every module when the model has no dependency edges. */
    fun dependencyScope(module: ModuleId): List<ModuleId> {
        if (dependencyEdges.isEmpty()) return model.modules.map { it.id }.sortedBy { it.value }
        val reached = linkedSetOf(module)
        val pending = ArrayDeque(listOf(module))
        while (pending.isNotEmpty()) {
            moduleDependencies[pending.removeFirst()].orEmpty().filter(reached::add).forEach(pending::addLast)
        }
        return reached.sortedBy { it.value }
    }

    /** The dependencies the modules of [scope] declare on other modules, module by module in the order of [scope]. */
    fun moduleDependenciesOf(scope: List<ModuleId>): List<ModuleDependency> =
        scope.flatMap { declaredDependencies[it].orEmpty() }

    private fun moduleDependency(edge: Relationship): ModuleDependency? {
        val from = edge.from.owningModule()
        val resolved = edge.resolution as? Resolution.Resolved
        val to = resolved?.target?.owningModule()
        val sourceSet = edge.scope.sourceSet ?: (edge.from as? NodeRef.OfSourceSet)?.id
        return if (from != null && to != null && from != to) {
            val heuristic = resolved.certainty == Certainty.HEURISTIC
            ModuleDependency(from, to, sourceSet, edge.attributes[CONFIGURATION], heuristic)
        } else {
            null
        }
    }

    private fun NodeRef.owningModule(): ModuleId? = when (this) {
        is NodeRef.OfModule -> id
        is NodeRef.OfSourceSet -> sourceSetsById[id]?.module
        is NodeRef.OfSymbol, is NodeRef.OfArtifact, is NodeRef.OfExternal -> null
    }

    private fun smallestId(modules: List<Module>): Module = modules.minBy { it.id.value }

    private companion object {
        const val CONFIGURATION = "configuration"
    }
}
