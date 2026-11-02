package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.model
import io.github.krosstack.kscope.analyzer.kotlin.planning.sourceSetId
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerCommand
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalyzerCapability
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSyntaxProducerTest {
    private val fixture = ProducerFixture()
    private val syntax = ProducerRef("kscope.analyzer.kotlin.syntax", "0.1.0")

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `the producer declares declarations only and consumes Kotlin and Java sources`() {
        val descriptor = fixture.syntaxProducerWith("default").descriptor
        assertEquals(setOf(AnalyzerCapability.DECLARATIONS), descriptor.capabilities)
        assertEquals(syntax, descriptor.producer)
        assertEquals(setOf("kotlin.source", "java.source"), descriptor.consumes.map { it.id }.toSet())
    }

    @Test
    fun `batches of a declare request are adopted under the syntax producer`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib))
        val batches = fixture.run(fixture.syntaxProducerWith("default"), request)
        assertEquals(listOf(fixture.app.value, fixture.lib.value), batches.map { it.owner.key })
        batches.forEach { assertEquals(syntax, it.producer) }
    }

    @Test
    fun `without dependency information the batches say so and drop the cycles that guessing creates`() {
        val bare = model { module(":a") { sourceSet("main") } }
        val only = sourceSetId(":a", "main")
        val request = fixture.requestFor(listOf(only), everything = listOf(only), buildModel = bare)
        val codes = fixture.run(fixture.syntaxProducerWith("cyclic"), request).single().diagnosticCodes()
        assertTrue("kotlin.dependencies-assumed" in codes)
        assertTrue("kotlin.cyclic-module-reference" !in codes)
    }

    @Test
    fun `a failing worker gives the units it did not answer an error batch`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib))
        val batches = fixture.run(fixture.syntaxProducerWith("fail"), request)
        val failed = batches.single { "kotlin.worker-failed" in it.diagnosticCodes() }
        assertEquals(fixture.lib.value, failed.owner.key)
        assertContains(failed.diagnostics().single().message, "kaput")
    }

    @Test
    fun `a missing worker gives every unit one error batch`() {
        val producer = KotlinSyntaxProducer(WorkerLauncher({ WorkerCommand.Missing("it is not installed") }))
        val batches = fixture.run(producer, fixture.requestFor(listOf(fixture.app)))
        assertEquals("kotlin.worker-unavailable", batches.single().diagnostics().single().code.value)
        assertEquals(syntax, batches.single().producer)
    }

    @Test
    fun `no worker is started when there is nothing to produce`() {
        var started = 0
        val producer = KotlinSyntaxProducer(
            WorkerLauncher({
                started++
                WorkerCommand.Missing("none")
            }),
        )
        assertEquals(emptyList(), fixture.run(producer, fixture.requestFor(emptyList())))
        assertEquals(0, started)
    }
}
