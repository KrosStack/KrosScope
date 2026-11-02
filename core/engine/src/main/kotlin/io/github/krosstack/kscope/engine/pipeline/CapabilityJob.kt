package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.incremental.PlannedWork
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.BuildModel

/**
 * The units to hand to a capability, and the subset whose results are stored. A job confined to a repository gives
 * the capability the build model and the artifacts of that repository only.
 */
internal class CapabilityJob(
    val producer: ProducerRef,
    val requested: List<PlannedWork>,
    val store: List<PlannedUnit>,
    val confinedTo: RepositoryId? = null,
) {
    fun modelIn(whole: BuildModel): BuildModel {
        if (confinedTo == null) return whole
        val roots = whole.buildRoots.filter { it.repository == confinedTo }
        val rootIds = roots.map { it.id }.toSet()
        val modules = whole.modules.filter { it.buildRoot in rootIds }
        val moduleIds = modules.map { it.id }.toSet()
        return BuildModel(
            buildRoots = roots,
            modules = modules,
            targets = whole.targets.filter { it.module in moduleIds },
            variants = whole.variants.filter { it.module in moduleIds },
            sourceSets = whole.sourceSets.filter { it.module in moduleIds },
            dependencies = whole.dependencies,
        )
    }

    fun artifactsIn(all: List<Artifact>): List<Artifact> =
        if (confinedTo == null) all else all.filter { it.ref.repository == confinedTo }
}
