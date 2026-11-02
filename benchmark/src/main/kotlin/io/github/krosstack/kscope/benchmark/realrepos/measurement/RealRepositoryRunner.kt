package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.model.context.TokenCount
import java.nio.file.Path

internal data class RealRunOptions(
    val output: Path,
    val identity: String,
    val split: RealSplit? = null,
    val repository: String? = null,
    val budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
)

internal class RealRepositoryRunner {
    fun run(manifest: RealRepositoryManifest, options: RealRunOptions): List<RealRepositoryMeasurement> {
        RealRunValidation.validate(manifest, options)
        val storage = RealRunStorage(options.output)
        storage.bind(manifest, options.identity, options.budgets)
        val repositories = manifest.repositories.associateBy { it.id }
        val tasks = manifest.tasks.filter {
            (options.split == null || it.split == options.split) &&
                (options.repository == null || it.repository == options.repository)
        }
        tasks.forEach { task ->
            val previous = storage.load(task, options.identity)
            if (previous?.status == RealMeasurementStatus.COMPLETE) {
                System.err.println("real-repos ${task.repository} ${task.id} completed-record-resumed")
            } else {
                val snapshot = RealWorkspaceSnapshot(repositories, task, storage)
                RealTaskRun(snapshot, task, options.identity, storage, options.budgets).execute().also(storage::save)
            }
        }
        return storage.records(tasks)
    }
}
