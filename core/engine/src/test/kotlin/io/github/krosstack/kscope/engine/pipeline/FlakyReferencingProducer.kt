package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.FAKE_NAMESPACE
import io.github.krosstack.kscope.engine.descriptorOf
import io.github.krosstack.kscope.engine.kotlinSource
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest

/** Answers each unit with an error only while [failing], and with one symbolic reference named after its file after. */
internal class FlakyReferencingProducer : FactProducer {
    override val descriptor = descriptorOf("flaky-referencing", kotlinSource, OwnershipKind.ARTIFACT)
    var failing = true

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val facts = if (failing) listOf(failure()) else listOf(referenceFor(request, unit.artifacts.single()))
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint("ignored"), facts))
        }
    }

    private fun failure() = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.ERROR, ReasonCode("fake.failed"), "worker ran out of memory", null),
    )

    private fun referenceFor(request: ProduceRequest, artifact: Artifact): Fact {
        val key = String(request.files.read(artifact.ref)).lineSequence().first().trim()
        return Fact.OfSymbolicReference(
            SymbolicReference(
                from = NodeRef.OfArtifact(artifact.ref),
                kind = RelationKind.REFERENCES,
                subKind = null,
                namespace = FAKE_NAMESPACE,
                key = key,
                site = SourceLocation(artifact.ref, TextRange(1, 1, 1, key.length + 1)),
                scope = Scope(sourceSet = artifact.sourceSet),
                evidenceClass = EvidenceClass.TEXTUAL,
            ),
        )
    }
}
