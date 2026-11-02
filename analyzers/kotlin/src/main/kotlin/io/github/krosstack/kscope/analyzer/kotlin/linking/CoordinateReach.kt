package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery

/**
 * Which modules a module reaches over `DEPENDS_ON` edges, the edges between repositories included, and which repository
 * a source set belongs to.
 */
internal class CoordinateReach(private val graph: GraphReader) {
    private val structure = graph.structure()
    private val moduleOfSourceSet = structure.sourceSets.associate { it.id to it.module }
    private val repositoryOfRoot = structure.buildRoots.associate { it.id to it.repository }
    private val repositoryOfModule = structure.modules.associate { it.id to repositoryOfRoot[it.buildRoot] }
    private val dependencies = ModuleDependencies(
        structure.sourceSets,
        graph.relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), limit = ALL)),
    )
    private val reached = mutableMapOf<ModuleId, Set<ModuleId>>()

    fun moduleOf(sourceSet: SourceSetId?): ModuleId? = sourceSet?.let(moduleOfSourceSet::get)

    fun repositoryOf(sourceSet: SourceSetId?): RepositoryId? = moduleOf(sourceSet)?.let(repositoryOfModule::get)

    fun moduleOfReference(edge: Relationship): ModuleId? =
        moduleOf(edge.scope.sourceSet) ?: moduleOf(edge.site?.let { graph.artifact(it.artifact)?.sourceSet })

    fun reaches(from: ModuleId, module: ModuleId): Boolean =
        module in reached.getOrPut(from) { dependencies.closure(from).toSet() }
}
