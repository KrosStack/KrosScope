package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.PlanInput
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.worker.WorkerModule

internal class ConsumedSources(private val request: ProduceRequest, consumedTypes: Set<String>) {
    private val artifacts: List<Artifact> = (request.units.flatMap { it.artifacts } + request.artifacts)
        .filter { it.type.id in consumedTypes }
        .distinctBy { it.ref }

    fun planInput(): PlanInput {
        val filesBySourceSet = artifacts.mapNotNull { artifact ->
            artifact.sourceSet?.let { it to artifact.toWorkerFile(request.files) }
        }.groupBy({ it.first }, { it.second })
        return PlanInput(
            model = request.model,
            files = filesBySourceSet,
            syntheticSources = request.syntheticSources,
            classpaths = request.classpaths,
            requested = request.units.mapNotNull { it.sourceSet?.id }.toSet(),
        )
    }

    fun weigher(): (WorkerModule) -> Long {
        val sizes = artifacts.filter { it.sourceSet != null }
            .groupBy({ it.sourceSet }, { it.sizeBytes }).mapValues { (_, bytes) -> bytes.sum() }
        return { module -> module.sourceSets.filter { it.emitFactsFor }.sumOf { sizes[it.sourceSet] ?: 0L } }
    }
}
