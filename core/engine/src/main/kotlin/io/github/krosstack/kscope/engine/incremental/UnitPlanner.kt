package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.build.ModelIndex
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.engine.pipeline.PlannedUnit
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.InputBasis
import io.github.krosstack.kscope.spi.ProducerDescriptor

internal class PlannedWork(val unit: PlannedUnit, val analysis: AnalysisUnit)

/**
 * Decides which ownership units a capability has and fingerprints each one. A unit exists only where the capability
 * consumes at least one artifact. [artifacts] holds the artifacts that may be handed to capabilities.
 *
 * An artifact unit changes with its artifact and with the definitions of its module and source set, because facts
 * carry what the build model says about them. A module unit changes with the artifacts and definitions of its module
 * and, when the model knows dependencies, of every module it depends on transitively. A unit of a wider scope
 * changes with its artifacts and with the build model. The one unit of a producer whose input is the repository
 * revision holds every artifact and changes with the revision and the paths of the artifacts, not with their content.
 */
internal class UnitPlanner(
    private val repositories: List<Repository>,
    private val artifacts: List<Artifact>,
    private val index: ModelIndex,
    private val digests: ScopeDigests,
) {
    private val sourceSetPlanner = SourceSetUnitPlanner(index, digests)

    fun plan(descriptor: ProducerDescriptor, api: ProducerApiDigests? = null): List<PlannedWork> {
        if (descriptor.inputs == InputBasis.REPOSITORY_REVISION) return revisionWork(descriptor.producer)
        val consumedTypes = descriptor.consumes.map { it.id }.toSet()
        val consumed = artifacts.filter { it.type.id in consumedTypes }.sortedBy { it.ref.path.value }
        val producer = descriptor.producer
        val work = when (descriptor.granularity) {
            OwnershipKind.ARTIFACT -> consumed.map { artifactWork(producer, it) }
            OwnershipKind.SOURCE_SET -> sourceSetPlanner.plan(producer, consumed, api)
            OwnershipKind.MODULE -> moduleWork(producer, consumed)
            OwnershipKind.BUILD_ROOT -> scopedWork(producer, OwnershipKind.BUILD_ROOT, consumed, ::buildRootKey)
            OwnershipKind.REPOSITORY -> scopedWork(producer, OwnershipKind.REPOSITORY, consumed, ::repositoryKey)
            OwnershipKind.WORKSPACE -> scopedWork(producer, OwnershipKind.WORKSPACE, consumed) { WORKSPACE_KEY }
        }
        return work.sortedBy { it.unit.owner.key }
    }

    private fun revisionWork(producer: ProducerRef): List<PlannedWork> {
        val byRepository = artifacts.groupBy { it.ref.repository }
        return repositories.mapNotNull { repository ->
            byRepository[repository.id]?.let { revisionWork(producer, repository, it) }
        }
    }

    private fun revisionWork(producer: ProducerRef, repository: Repository, members: List<Artifact>): PlannedWork {
        val owner = OwnershipUnit(OwnershipKind.REPOSITORY, repository.id.value)
        val sorted = members.sortedBy { it.ref.path.value }
        val fingerprint = FingerprintBuilder()
            .add(producer.id)
            .add(producer.version)
            .add(repository.headRevision.orEmpty())
            .addAll(sorted.map { it.ref.path.value })
            .build()
        return PlannedWork(PlannedUnit(producer, owner, fingerprint), AnalysisUnit(owner, sorted, null, null))
    }

    private fun moduleKey(artifact: Artifact) = index.moduleContaining(artifact.ref)?.id?.value

    private fun repositoryKey(artifact: Artifact) = artifact.ref.repository.value

    private fun buildRootKey(artifact: Artifact) = index.buildRootContaining(artifact.ref)?.id?.value

    private fun artifactWork(producer: ProducerRef, artifact: Artifact): PlannedWork {
        val owner = OwnershipUnit(OwnershipKind.ARTIFACT, "${artifact.ref.repository.value}:${artifact.ref.path.value}")
        val sourceSet = artifact.sourceSet?.let(index::sourceSet)
        val module = sourceSet?.let { index.module(it.module) } ?: index.moduleContaining(artifact.ref)
        val fingerprint = FingerprintBuilder()
            .add(producer.id)
            .add(producer.version)
            .add(artifact.contentHash.value)
            .add(artifact.sourceSet?.value.orEmpty())
            .add(module?.let(digests::module).orEmpty())
            .add(sourceSet?.let(digests::definition).orEmpty())
            .build()
        return PlannedWork(
            PlannedUnit(producer, owner, fingerprint),
            AnalysisUnit(owner, listOf(artifact), sourceSet, module),
        )
    }

    private fun moduleWork(producer: ProducerRef, consumed: List<Artifact>): List<PlannedWork> {
        val byModule = consumed.mapNotNull { artifact -> moduleKey(artifact)?.let { it to artifact } }
            .groupBy({ it.first }, { it.second })
        return byModule.map { (key, members) ->
            val module = ModuleId(key)
            val scope = if (index.hasDependencyEdges) index.dependencyScope(module) else listOf(module)
            val owner = OwnershipUnit(OwnershipKind.MODULE, key)
            val fingerprint = FingerprintBuilder()
                .add(producer.id)
                .add(producer.version)
                .addAll(scope.flatMap { moduleParts(it, byModule[it.value].orEmpty()) })
                .build()
            val analysis = AnalysisUnit(owner, members, null, index.module(module))
            PlannedWork(PlannedUnit(producer, owner, fingerprint), analysis)
        }
    }

    private fun moduleParts(module: ModuleId, members: List<Artifact>): List<String> {
        val definition = index.module(module)?.let(digests::module).orEmpty()
        val sourceSets = index.sourceSetsOf(module).map(digests::definition)
        return listOf(module.value, definition) + sourceSets + members.flatMap(::artifactParts)
    }

    private fun scopedWork(
        producer: ProducerRef,
        kind: OwnershipKind,
        consumed: List<Artifact>,
        scopeOf: (Artifact) -> String?,
    ): List<PlannedWork> {
        val grouped = consumed.mapNotNull { artifact -> scopeOf(artifact)?.let { it to artifact } }
            .groupBy({ it.first }, { it.second })
        return grouped.map { (key, members) ->
            val owner = OwnershipUnit(kind, key)
            val fingerprint = FingerprintBuilder()
                .add(producer.id)
                .add(producer.version)
                .add(digests.model(index.model))
                .addAll(members.flatMap(::artifactParts))
                .build()
            PlannedWork(PlannedUnit(producer, owner, fingerprint), AnalysisUnit(owner, members, null, null))
        }
    }

    private fun artifactParts(artifact: Artifact) =
        listOf(artifact.ref.path.value, artifact.contentHash.value, artifact.sourceSet?.value.orEmpty())

    private companion object {
        const val WORKSPACE_KEY = "workspace"
    }
}
