package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerCommand
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ProducerRef
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** A scan goes on when the worker is missing, fails or hangs: the units it did not answer carry an error. */
class ProducerFailureTest {
    private val fixture = ProducerFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `a missing worker gives every unit one error batch so that the scan can go on`() {
        val producer = KotlinAnalyzerProducer(WorkerLauncher({ WorkerCommand.Missing("it is not installed") })) {}
        val batches = fixture.run(producer, fixture.requestFor(listOf(fixture.app, fixture.lib)))
        assertEquals(listOf(fixture.app.value, fixture.lib.value), batches.map { it.owner.key })
        batches.forEach { batch ->
            val diagnostic = batch.diagnostics().single()
            assertEquals(1, batch.facts.size)
            assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
            assertEquals("kotlin.worker-unavailable", diagnostic.code.value)
            assertContains(diagnostic.message, "it is not installed")
            assertEquals(ProducerRef("kscope.analyzer.kotlin", "0.1.0"), batch.producer)
        }
    }

    @Test
    fun `a failing worker fails the units it did not answer and keeps the batches it did`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib))
        val batches = fixture.run(fixture.producerWith("fail"), request)
        val answered = batches.single { "fake.analysed" in it.diagnosticCodes() }
        val failed = batches.single { it !== answered }
        assertEquals(fixture.app.value, answered.owner.key)
        assertEquals(fixture.lib.value, failed.owner.key)
        assertEquals("kotlin.worker-failed", failed.diagnostics().single().code.value)
        assertContains(failed.diagnostics().single().message, "kaput")
    }

    @Test
    fun `batches reach the sink before a hanging worker times out`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib))
        val batches = fixture.run(fixture.producerWith("hang", responseTimeout = 2.seconds), request)
        assertEquals(listOf(fixture.app.value, fixture.lib.value), batches.map { it.owner.key })
        assertContains(batches.first().diagnosticCodes(), "fake.analysed")
        assertContains(batches.last().diagnostics().single().message, "did not answer within 2s")
    }

    @Test
    fun `no worker is started when there is nothing to produce`() {
        var started = 0
        val launcher = WorkerLauncher({
            started++
            WorkerCommand.Missing("none")
        })
        val producer = KotlinAnalyzerProducer(launcher) {}
        assertEquals(emptyList(), fixture.run(producer, fixture.requestFor(emptyList())))
        assertEquals(0, started)
    }
}
