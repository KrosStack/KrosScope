package io.github.krosstack.kscope.analyzer.kotlin.worker.declare

import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.DeclarationPass
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.ModulePlan
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.worker.WorkerModule

internal const val NOT_ANALYSED = "kotlin.references.not-analysed-at-syntax"

private const val NOT_ANALYSED_MESSAGE =
    "References, calls and inheritance of this source set were not analysed; the scan level is syntax"

/**
 * The facts of the syntax level: for each requested source set the declarations read from its syntax trees, then one
 * note that nothing was resolved. Annotation names that the compiler would qualify stay as written.
 */
internal class SyntaxFactPass(private val producer: ProducerRef, parser: ScriptParser) {
    private val declarations = DeclarationPass(producer) { null }
    private val reader = SyntaxSourceReader(parser)

    fun batchSequence(modules: List<WorkerModule>): Sequence<FactBatch> {
        val problems = ModulePlan.of(modules).problems
        return reader.sourceSetsOf(modules).map { sourceSet ->
            FactBatch(
                producer,
                OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.source.sourceSet.value),
                sourceSet.source.fingerprint,
                declarations.factsOf(problems, sourceSet) + notAnalysed(),
            )
        }
    }

    private fun notAnalysed(): Fact = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.INFO, ReasonCode(NOT_ANALYSED), NOT_ANALYSED_MESSAGE, null),
    )
}
