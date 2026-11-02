package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.build.ModelIndex
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.engine.pipeline.PlannedUnit
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.AnalysisUnit

/**
 * Source set units change with their own content and the content or API of the other source sets in their scope.
 * Synthetic sources, classpaths and source set definitions always count in full. So do the dependencies between the
 * modules of the scope, with their configuration and source set, because they decide which of those modules a source
 * set can see.
 */
internal class SourceSetUnitPlanner(private val index: ModelIndex, private val digests: ScopeDigests) {
    fun plan(producer: ProducerRef, consumed: List<Artifact>, api: ProducerApiDigests? = null): List<PlannedWork> {
        val bySourceSet = consumed.mapNotNull { artifact -> artifact.sourceSet?.let { it to artifact } }
            .groupBy({ it.first }, { it.second })
        val artifactDigests = SourceSetArtifactDigests(bySourceSet, api)
        return bySourceSet.keys.sortedBy { it.value }.mapNotNull { id ->
            index.sourceSet(id)?.let { workFor(producer, it, bySourceSet.getValue(id), artifactDigests) }
        }
    }

    private fun workFor(
        producer: ProducerRef,
        sourceSet: SourceSet,
        members: List<Artifact>,
        artifactDigests: SourceSetArtifactDigests,
    ): PlannedWork {
        val owner = OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.id.value)
        val scope = index.dependencyScope(sourceSet.module)
        val fingerprint = FingerprintBuilder()
            .add(producer.id)
            .add(producer.version)
            .apply { artifactDigests.version?.let { add(it) } }
            .add(sourceSet.id.value)
            .addAll(scope.map { it.value })
            .addAll(index.moduleDependenciesOf(scope).flatMap { it.fingerprintParts })
            .addAll(
                scope.flatMap { module ->
                    index.sourceSetsOf(module).flatMap { member ->
                        listOf(
                            member.id.value,
                            digests.definition(member),
                            artifactDigests.digest(member.id, sourceSet.id),
                            digests.synthetic(member.id),
                            digests.classpath(member.id),
                        )
                    }
                },
            )
            .build()
        return PlannedWork(
            PlannedUnit(producer, owner, fingerprint),
            AnalysisUnit(owner, members, sourceSet, index.module(sourceSet.module)),
        )
    }
}
