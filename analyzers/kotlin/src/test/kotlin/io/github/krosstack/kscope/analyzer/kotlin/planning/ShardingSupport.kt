package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.worker.WorkerModule

internal fun weights(vararg entries: Pair<String, Long>): (WorkerModule) -> Long {
    val byName = entries.toMap()
    return { module -> byName[module.name] ?: 0L }
}

internal fun referencesOf(module: WorkerModule): List<String> = module.dependsOn + module.dependencies + module.friends

internal fun unitsOf(model: BuildModel): List<AnalysisUnit> = model.sourceSets.map { sourceSet ->
    val module = model.modules.single { it.id == sourceSet.module }
    AnalysisUnit(OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.id.value), emptyList(), sourceSet, module)
}

internal fun emitting(modules: List<WorkerModule>): List<String> =
    modules.filter { module -> module.sourceSets.any { it.emitFactsFor } }.map { it.name }

internal fun everyModule(weight: Long): (WorkerModule) -> Long = { module ->
    if (module.sourceSets.any { it.emitFactsFor }) weight else 0L
}
