package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet

/** Worker modules without files or edges, to see which batches a fake worker sends back. */
object ModuleSamples {
    fun module(name: String, vararg sourceSets: String) = WorkerModule(
        name = name,
        platform = Platform.JVM,
        sourceSets = sourceSets.map(::sourceSet),
        dependsOn = emptyList(),
        dependencies = emptyList(),
        friends = emptyList(),
        classpath = emptyList(),
    )

    private fun sourceSet(name: String) = WorkerSourceSet(
        sourceSet = SourceSetId("repo//:m@$name"),
        module = ModuleId("repo//:m"),
        files = emptyList(),
        syntheticSources = emptyList(),
        emitFactsFor = true,
        fingerprint = Fingerprint("fingerprint-$name"),
    )
}
