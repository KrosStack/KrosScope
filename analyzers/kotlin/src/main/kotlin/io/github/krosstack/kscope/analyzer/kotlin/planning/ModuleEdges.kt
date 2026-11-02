package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.BuildModel

/**
 * A dependency of one Gradle module on another. A null [scope] means that it holds for every source set;
 * [scopePlatforms] are the platforms that compile the main source set that [scope] names: those of its targets, and
 * common code when there are several. They are empty for any other scope. An edge is [heuristic] when the build model
 * is not sure of it.
 */
internal data class ModuleEdge(
    val from: ModuleId,
    val to: ModuleId,
    val scope: SourceSetId?,
    val configuration: String?,
    val scopePlatforms: Set<Platform>,
    val heuristic: Boolean,
) {
    val exposesTarget: Boolean = configuration?.let { it == API || it.endsWith(API_SUFFIX) } == true

    private companion object {
        const val API = "api"
        const val API_SUFFIX = "Api"
    }
}

/** The module-to-module dependencies of a build model. External coordinates are not modules and are ignored. */
internal class ModuleEdges(private val model: BuildModel, keep: (ModuleEdge) -> Boolean = { true }) {
    private val sourceSetsById = model.sourceSets.associateBy { it.id }
    private val platformOfTarget = model.targets.associate { it.id to it.platform }
    private val dependencyRelationships = model.dependencies.filter { it.kind == RelationKind.DEPENDS_ON }
    private val byFrom: Map<ModuleId, List<ModuleEdge>> =
        dependencyRelationships.mapNotNull(::edgeOf).filter(keep).groupBy { it.from }

    /** False for the structural build model, which knows no dependencies at all. */
    val hasInformation: Boolean = dependencyRelationships.isNotEmpty()

    fun from(module: ModuleId): List<ModuleEdge> = byFrom[module].orEmpty()

    /** The same dependencies without the heuristic ones. */
    fun withoutHeuristic(): ModuleEdges = ModuleEdges(model) { !it.heuristic }

    /** The roots and every module they depend on, directly or not. */
    fun reachableFrom(roots: Collection<ModuleId>): Set<ModuleId> {
        val reached = LinkedHashSet(roots)
        val pending = ArrayDeque(roots)
        while (pending.isNotEmpty()) {
            from(pending.removeFirst()).map { it.to }.filter(reached::add).forEach(pending::addLast)
        }
        return reached
    }

    private fun edgeOf(relationship: Relationship): ModuleEdge? {
        val from = moduleOf(relationship.from)
        val resolved = relationship.resolution as? Resolution.Resolved
        val to = resolved?.target?.let(::moduleOf)
        val scope = relationship.scope.sourceSet ?: (relationship.from as? NodeRef.OfSourceSet)?.id
        val heuristic = resolved?.certainty == Certainty.HEURISTIC
        return if (from == null || to == null || from == to) {
            null
        } else {
            ModuleEdge(from, to, scope, relationship.attributes[CONFIGURATION], platformsOf(scope), heuristic)
        }
    }

    private fun platformsOf(scope: SourceSetId?): Set<Platform> {
        val sourceSet = scope?.let(sourceSetsById::get)?.takeIf { it.role == SourceSetRole.MAIN } ?: return emptySet()
        val own = sourceSet.targets.mapNotNull(platformOfTarget::get).toSet()
        return if (own.size > 1) own + Platform.COMMON else own.ifEmpty { setOf(Platform.JVM) }
    }

    private fun moduleOf(node: NodeRef): ModuleId? = when (node) {
        is NodeRef.OfModule -> node.id
        is NodeRef.OfSourceSet -> sourceSetsById[node.id]?.module
        is NodeRef.OfSymbol, is NodeRef.OfArtifact, is NodeRef.OfExternal -> null
    }

    private companion object {
        const val CONFIGURATION = "configuration"
    }
}
