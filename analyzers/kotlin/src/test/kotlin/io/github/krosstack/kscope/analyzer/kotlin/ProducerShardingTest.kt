package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.FakeWorkerHarness
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerCommand
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerConcurrency
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerTimeouts
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ProduceRequest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProducerShardingTest {
    private val fixture = ProducerFixture()
    private val harness = FakeWorkerHarness()
    private val launches = AtomicInteger()

    @AfterTest
    fun cleanUp() {
        harness.close()
        fixture.close()
    }

    private fun inflated(artifact: Artifact) = artifact.copy(sizeBytes = SHARE_BYTES)

    private fun heavyRequest(requested: List<SourceSetId>): ProduceRequest {
        val units = requested.map { id ->
            val unit = fixture.unitOf(id)
            AnalysisUnit(unit.owner, unit.artifacts.map(::inflated), unit.sourceSet, unit.module)
        }
        val request = fixture.requestFor(requested)
        return request.copy(units = units, artifacts = request.artifacts.map(::inflated))
    }

    private fun producer(workers: Int, mode: (Int) -> WorkerCommand = { harness.command("ok") }) =
        KotlinAnalyzerProducer(WorkerLauncher({ mode(launches.incrementAndGet()) }), WorkerConcurrency(workers)) {}

    private fun commandFor(launch: Int, first: WorkerCommand): WorkerCommand =
        if (launch == 1) first else harness.command("ok")

    private fun fingerprints(batches: List<FactBatch>) = batches.associate { it.owner.key to it.inputFingerprint }

    private fun answers(batches: List<FactBatch>): Map<String, List<String>> =
        batches.associate { it.owner.key to it.diagnostics().map { diagnostic -> diagnostic.message } }

    @Test
    fun `the modules are analysed by one worker each and the answers equal those of a single worker`() {
        val request = heavyRequest(listOf(fixture.app, fixture.lib, fixture.other))

        val single = fixture.run(producer(workers = 1), request)
        assertEquals(1, launches.get())
        launches.set(0)
        val shared = fixture.run(producer(workers = 3), request)

        assertEquals(3, launches.get())
        assertEquals(3, shared.size)
        assertEquals(answers(single), answers(shared))
        assertEquals(fingerprints(single), fingerprints(shared))
    }

    @Test
    fun `a worker that cannot start fails only the units it was given`() {
        val request = heavyRequest(listOf(fixture.app, fixture.lib, fixture.other))
        val missing = WorkerCommand.Missing("not installed")

        val batches = fixture.run(producer(workers = 3) { number -> commandFor(number, missing) }, request)

        assertEquals(3, batches.size)
        assertEquals(1, batches.count { "kotlin.worker-unavailable" in it.diagnosticCodes() })
        assertEquals(2, batches.count { "fake.analysed" in it.diagnosticCodes() })
    }

    @Test
    fun `little source is analysed by a single worker however many are allowed`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib, fixture.other))

        fixture.run(producer(workers = 8), request)

        assertEquals(1, launches.get())
    }

    @Test
    fun `a worker that stops answering is reported together with the settings that bound its memory`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib))
        val impatient = WorkerTimeouts(handshake = 1.minutes, response = 2.seconds, shutdown = 10.seconds)
        val hanging = KotlinAnalyzerProducer(harness.launcher("hang", impatient)) {}

        val batches = fixture.run(hanging, request)

        assertContains(batches.last().diagnostics().single().message, "KSCOPE_WORKER_HEAP")
        assertContains(batches.last().diagnostics().single().message, "KSCOPE_WORKERS")
    }

    private companion object {
        const val SHARE_BYTES = 3L * 1024 * 1024
    }
}
