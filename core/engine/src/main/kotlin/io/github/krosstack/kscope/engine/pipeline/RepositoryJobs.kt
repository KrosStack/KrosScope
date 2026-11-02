package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.ModelIndex
import io.github.krosstack.kscope.engine.incremental.PlannedWork
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.RepositoryId

/**
 * Splits a job into the jobs a capability runs for. The units of a repository capability are split by repository.
 * The source sets of a build model without dependency edges are split by repository too and each part is confined to
 * its repository: without edges the analysis assumes that every module sees every other module, and the repositories
 * of a workspace do not see each other unless a dependency says so.
 */
internal class RepositoryJobs(private val index: ModelIndex) {
    private val repositoryOfRoot = index.model.buildRoots.associate { it.id to it.repository }
    private val repositories = repositoryOfRoot.values.toSet()

    fun split(job: CapabilityJob): List<CapabilityJob> = when (job.requested.map { it.unit.owner.kind }.toSet()) {
        setOf(OwnershipKind.REPOSITORY) -> ofRepositories(job)
        setOf(OwnershipKind.SOURCE_SET) -> if (index.hasDependencyEdges) listOf(job) else confined(job)
        else -> listOf(job)
    }

    private fun ofRepositories(job: CapabilityJob): List<CapabilityJob> =
        job.requested.groupBy { it.unit.owner.key }.map { (key, work) ->
            CapabilityJob(job.producer, work, job.store.filter { it.owner.key == key })
        }

    private fun confined(job: CapabilityJob): List<CapabilityJob> {
        if (repositories.size < 2) return listOf(job)
        return job.requested.groupBy(::repositoryOf).map { (repository, work) ->
            val owners = work.map { it.unit.owner }.toSet()
            CapabilityJob(job.producer, work, job.store.filter { it.owner in owners }, repository)
        }
    }

    private fun repositoryOf(work: PlannedWork): RepositoryId? =
        work.analysis.module?.let { repositoryOfRoot[it.buildRoot] }
}
