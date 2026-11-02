package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisSessions
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.DeclarationPass
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.worker.WorkerModule

/** Runs the declaration pass in this process, the way the worker does for an `Analyze` request. */
fun analyze(vararg modules: WorkerModule): List<FactBatch> = analyze(modules.toList())

fun analyze(modules: List<WorkerModule>): List<FactBatch> =
    AnalysisSessions.open(modules).use { DeclarationPass(WorkerIdentity.producer).batchesOf(it) }

/**
 * Runs the declaration and the resolution pass, the way the worker does, and checks the integrity rule on the way:
 * no relationship points at a workspace symbol that the same run did not emit. Symbols for which [synthesized] holds
 * stand in for generated code and are emitted by whoever generates it.
 */
fun analyzeAll(vararg modules: WorkerModule): List<FactBatch> = analyzeAll(modules.toList())

fun analyzeAll(modules: List<WorkerModule>, synthesized: (SymbolId) -> Boolean = { false }): List<FactBatch> {
    val batches = AnalysisSessions.open(modules).use { FactPass(WorkerIdentity.producer).batchesOf(it) }
    Integrity.assertNoDanglingTargets(batches, modules, synthesized)
    return batches
}

fun List<FactBatch>.symbols(): List<Symbol> =
    flatMap { batch -> batch.facts.filterIsInstance<Fact.OfSymbol>().map { it.value } }

fun List<FactBatch>.relationships(): List<Relationship> =
    flatMap { batch -> batch.facts.filterIsInstance<Fact.OfRelationship>().map { it.value } }

fun List<FactBatch>.diagnostics(): List<Diagnostic> =
    flatMap { batch -> batch.facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value } }
