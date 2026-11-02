package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.worker.BatchParts
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerResponse

/**
 * The answers of the fake worker to an analysis: one batch per source set whose facts are wanted, holding a diagnostic
 * that describes the module the source set was analysed in. Mode `all` answers for every source set and mode `cyclic`
 * adds the warning that guessed dependencies provoke. The modes that start with `parts` answer with a batch of
 * [PART_FACTS] facts that travels in several messages of [PART_SIZE] facts.
 */
object FakeBatches {
    const val FAKE_VERSION = "9.9.9"

    val producer = ProducerRef("kscope.analyzer.kotlin", FAKE_VERSION)

    const val PART_FACTS = 1_300
    const val PART_SIZE = 500

    fun of(mode: String, requestId: String, module: WorkerModule): List<WorkerResponse> =
        module.sourceSets.filter { it.emitFactsFor || mode == "all" }.flatMap { sourceSet ->
            val owner = OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.sourceSet.value)
            val facts = if (mode.startsWith("parts")) many() else few(module, mode)
            BatchParts.split(requestId, FactBatch(producer, owner, sourceSet.fingerprint, facts), PART_SIZE)
        }

    private fun few(module: WorkerModule, mode: String): List<Fact> =
        listOf(describing(module)) + if (mode == "cyclic") listOf(cyclicWarning()) else emptyList()

    private fun many(): List<Fact> = (1..PART_FACTS).map { number ->
        Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.INFO, ReasonCode("fake.fact"), "fact $number", null))
    }

    private fun describing(module: WorkerModule): Fact {
        val sourceSets = module.sourceSets.map {
            "${it.sourceSet.value}:files=${it.files.size}:synthetic=${it.syntheticSources.size}:emit=${it.emitFactsFor}"
        }
        val description = "module=${module.name} platform=${module.platform} dependsOn=${module.dependsOn} " +
            "dependencies=${module.dependencies} friends=${module.friends} classpath=${module.classpath} " +
            "sourceSets=$sourceSets"
        return Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.INFO, ReasonCode("fake.analysed"), description, null))
    }

    private fun cyclicWarning(): Fact = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.WARNING, ReasonCode("kotlin.cyclic-module-reference"), "cycle", null),
    )
}
