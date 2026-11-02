package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode

internal fun sampleBatch(owner: String, count: Int, fingerprint: String = "f1") = FactBatch(
    ProducerRef("kotlin-worker", "1.0.0"),
    OwnershipUnit(OwnershipKind.SOURCE_SET, owner),
    Fingerprint(fingerprint),
    (1..count).map { sampleFact(it) },
)

private fun sampleFact(number: Int): Fact =
    Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.INFO, ReasonCode("t.$number"), "fact $number", null))
