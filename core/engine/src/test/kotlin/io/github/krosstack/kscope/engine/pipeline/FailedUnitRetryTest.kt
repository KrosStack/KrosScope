package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.descriptorOf
import io.github.krosstack.kscope.engine.kotlinSource
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Answers each unit with an error while [failing], with a note afterwards; [alsoNotes] adds a note to the error. */
private class FlakyProducer(private val alsoNotes: Boolean = false) : FactProducer {
    override val descriptor = descriptorOf("flaky", kotlinSource, OwnershipKind.SOURCE_SET)
    var failing = true
    var produced = 0
        private set

    override fun produce(request: ProduceRequest, sink: FactSink) {
        produced += request.units.size
        val error = Diagnostic(DiagnosticSeverity.ERROR, ReasonCode("fake.failed"), "worker ran out of memory", null)
        val note = Diagnostic(DiagnosticSeverity.INFO, ReasonCode("fake.analysed"), "analysed", null)
        val diagnostics = if (failing) listOf(error) + listOf(note).filter { alsoNotes } else listOf(note)
        request.units.forEach { unit ->
            val facts = diagnostics.map { Fact.OfDiagnostic(it) }
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint("ignored"), facts))
        }
    }
}

class FailedUnitRetryTest {
    @Test
    fun `a unit whose only answer is an error is produced again, and its error is kept meanwhile`() {
        val producer = FlakyProducer()
        scanningSample(coreRegistry(producer)) { _, engine ->
            val first = engine.update()
            val units = producer.produced
            assertTrue(units > 0)
            assertTrue(first.diagnostics.any { it.code.value == "fake.failed" })

            engine.update()
            assertEquals(2 * units, producer.produced)

            producer.failing = false
            val recovered = engine.update()
            assertEquals(3 * units, producer.produced)
            assertTrue(recovered.diagnostics.none { it.code.value == "fake.failed" })

            engine.update()
            assertEquals(3 * units, producer.produced)
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `a unit with an error and other facts stays current`() {
        val producer = FlakyProducer(alsoNotes = true)
        scanningSample(coreRegistry(producer)) { _, engine ->
            engine.update()
            val units = producer.produced
            assertTrue(units > 0)
            engine.update()
            assertEquals(units, producer.produced)
        }
    }
}
