package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisPlan
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode

internal object WorkerDiagnostics {
    const val UNAVAILABLE = "kotlin.worker-unavailable"
    const val FAILED = "kotlin.worker-failed"
    const val DEPENDENCIES_ASSUMED = "kotlin.dependencies-assumed"
    const val CYCLIC_MODULE_REFERENCE = "kotlin.cyclic-module-reference"

    private const val ASSUMED_MESSAGE = "The build model has no dependency information; every module was assumed to " +
        "see every other module that can be compiled against, so some references may resolve to the wrong module"

    fun error(code: String, message: String) =
        Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.ERROR, ReasonCode(code), message, null))

    /**
     * The batch as the engine expects it: from this producer, whatever version the worker names. Guessed dependencies
     * are announced once, by the batch of the plan's announcing source set, and the cycles that guessing creates
     * between modules are not reported as problems.
     */
    fun adopt(batch: FactBatch, producer: ProducerRef, plan: AnalysisPlan): FactBatch {
        if (!plan.dependenciesAssumed) return batch.copy(producer = producer)
        val facts = batch.facts.filterNot(::isCyclicModuleWarning)
        val announced = if (batch.owner.key == plan.assumptionAnnouncer?.value) facts + assumedNote() else facts
        return batch.copy(producer = producer, facts = announced)
    }

    private fun assumedNote() = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.INFO, ReasonCode(DEPENDENCIES_ASSUMED), ASSUMED_MESSAGE, null),
    )

    private fun isCyclicModuleWarning(fact: Fact) =
        fact is Fact.OfDiagnostic && fact.value.code.value == CYCLIC_MODULE_REFERENCE
}
