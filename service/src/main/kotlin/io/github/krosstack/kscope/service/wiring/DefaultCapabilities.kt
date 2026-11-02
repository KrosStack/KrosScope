package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.analyzer.gradle.GradleStaticProvider
import io.github.krosstack.kscope.analyzer.gradle.GradleToolingProvider
import io.github.krosstack.kscope.analyzer.kotlin.KotlinAnalyzerProducer
import io.github.krosstack.kscope.analyzer.kotlin.KotlinScriptOutliner
import io.github.krosstack.kscope.analyzer.kotlin.KotlinSyntaxProducer
import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings
import io.github.krosstack.kscope.analyzer.kotlin.linking.KotlinLinkers
import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.settings.AnalyzerSettings
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.ScriptOutliner
import java.nio.file.Path

internal object DefaultCapabilities {
    /**
     * The engine asks the Gradle tooling provider first, which executes build logic and is eligible only for a trusted
     * workspace; then the static Gradle provider, which executes nothing; and the structural provider last. The tooling
     * provider takes the facts that are read from files, such as the version catalog symbols, from the static one.
     * [workerSettings] is asked whenever the Kotlin workers are started. The groups that [selection] switches off
     * contribute no detector, synthesizer, producer or linker. At [ScanLevel.SYNTAX] the Kotlin and Java producer reads
     * declarations without resolving anything.
     */
    fun registry(
        indexDirectory: Path,
        workerSettings: () -> WorkerSettings = { WorkerSettings.NONE },
        selection: AnalyzerSettings = AnalyzerSettings.ALL,
        level: ScanLevel = ScanLevel.RESOLVED,
    ): CapabilityRegistry {
        val static = GradleStaticProvider(kotlinScriptOutliner(workerSettings))
        val groups = AnalyzerGroups.of(selection)
        return CapabilityRegistry(
            detectors = CoreCapabilities.detectors() + groups.flatMap { it.detectors },
            buildModelProviders = listOf(GradleToolingProvider(indexDirectory, static), static) +
                CoreCapabilities.buildModelProviders(),
            synthesizers = groups.flatMap { it.synthesizers },
            producers = listOf(kotlinProducer(workerSettings, level)) +
                groups.flatMap { it.producers },
            linkers = KotlinLinkers.linkers() + groups.flatMap { it.linkers },
        )
    }

    private fun kotlinProducer(workerSettings: () -> WorkerSettings, level: ScanLevel): FactProducer =
        if (level == ScanLevel.SYNTAX) {
            KotlinSyntaxProducer(workerSettings, workerOutput = {})
        } else {
            KotlinAnalyzerProducer(workerSettings, workerOutput = {})
        }

    /** The outliner for Kotlin build scripts that the static Gradle provider is given. */
    fun kotlinScriptOutliner(workerSettings: () -> WorkerSettings = { WorkerSettings.NONE }): ScriptOutliner =
        KotlinScriptOutliner(workerSettings, workerOutput = {})
}
