package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch

internal fun FactBatch.diagnostics(): List<Diagnostic> = facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value }

internal fun FactBatch.diagnosticCodes(): List<String> = diagnostics().map { it.code.value }

/** What the fake worker reports about the module a source set was analysed in. */
internal fun FactBatch.description(): String = diagnostics().single { it.code.value == "fake.analysed" }.message
