package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.model
import io.github.krosstack.kscope.analyzer.kotlin.planning.sourceSetId
import io.github.krosstack.kscope.analyzer.kotlin.process.FakeBatches
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the producer asks the worker to analyse, and what it does with the answers. */
class ProducerRequestMappingTest {
    private val fixture = ProducerFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `the producer declares what it consumes and for which ownership it produces`() {
        val descriptor = KotlinAnalyzerProducer().descriptor
        assertEquals(ProducerRef("kscope.analyzer.kotlin", "0.1.0"), descriptor.producer)
        assertEquals(setOf("kotlin.source", "java.source"), descriptor.consumes.map { it.id }.toSet())
        assertEquals(OwnershipKind.SOURCE_SET, descriptor.granularity)
        val capabilities = setOf(
            AnalyzerCapability.DECLARATIONS,
            AnalyzerCapability.RESOLVED_REFERENCES,
            AnalyzerCapability.CALLS,
            AnalyzerCapability.INHERITANCE,
        )
        assertEquals(capabilities, descriptor.capabilities)
    }

    @Test
    fun `facts are emitted for the requested units and their dependencies are context`() {
        val batches = fixture.run(fixture.producerWith("all"), fixture.requestFor(listOf(fixture.app)))
        val emitting = batches.filter { "emit=true" in it.description() }
        assertEquals(listOf(fixture.app.value, fixture.lib.value), batches.map { it.owner.key })
        assertEquals(listOf(fixture.app.value), emitting.map { it.owner.key })
        assertContains(emitting.single().description(), "dependencies=[${fixture.lib.value}]")
    }

    @Test
    fun `the files of units that are not produced are analysis context`() {
        val request = fixture.requestFor(listOf(fixture.app))
        val library = fixture.run(fixture.producerWith("all"), request).single { it.owner.key == fixture.lib.value }
        assertContains(library.description(), "${fixture.lib.value}:files=1:synthetic=0:emit=false")
    }

    @Test
    fun `synthetic sources and classpaths are handed to the worker`() {
        val stub = SyntheticSource("gen/R.kt", "kotlin", "object R", fixture.app, "android.resources")
        val request = fixture.requestFor(
            listOf(fixture.app),
            synthetic = listOf(stub),
            classpaths = mapOf(fixture.app to listOf("/libs/a.jar")),
        )
        val description = fixture.run(fixture.producerWith("ok"), request).single().description()
        assertContains(description, "${fixture.app.value}:files=1:synthetic=1:emit=true")
        assertContains(description, "classpath=[/libs/a.jar]")
    }

    @Test
    fun `a stub reaches the source set it is written for and no other`() {
        val stub = SyntheticSource("gen/R.java", "java", "class R {}", fixture.lib, "android.r")
        val request = fixture.requestFor(listOf(fixture.app), synthetic = listOf(stub))
        val batches = fixture.run(fixture.producerWith("all"), request)
        val description = batches.associate { it.owner.key to it.description() }
        assertContains(description.getValue(fixture.lib.value), "${fixture.lib.value}:files=1:synthetic=1:emit=false")
        assertContains(description.getValue(fixture.app.value), "${fixture.app.value}:files=1:synthetic=0:emit=true")
    }

    @Test
    fun `a source set that has nothing but a stub is analysed so that references to the stub bind`() {
        val stub = SyntheticSource("gen/R.java", "java", "class R {}", fixture.app, "android.r")
        val unit = fixture.unitOf(fixture.app).let { AnalysisUnit(it.owner, emptyList(), it.sourceSet, it.module) }
        val request = ProduceRequest(
            units = listOf(unit),
            model = fixture.model,
            files = fixture.files,
            syntheticSources = listOf(stub),
        )
        val description = fixture.run(fixture.producerWith("ok"), request).single().description()
        assertContains(description, "${fixture.app.value}:files=0:synthetic=1:emit=true")
    }

    @Test
    fun `batches are adopted by this producer whatever version the worker names`() {
        val request = fixture.requestFor(listOf(fixture.app, fixture.lib))
        val batches = fixture.run(fixture.producerWith("ok"), request)
        assertEquals(2, batches.size)
        assertTrue(batches.all { it.producer == ProducerRef("kscope.analyzer.kotlin", "0.1.0") })
        assertTrue(batches.none { it.producer.version == FakeBatches.FAKE_VERSION })
    }

    @Test
    fun `without dependency information the batches say so and drop the cycles that guessing creates`() {
        val bare = model { module(":a") { sourceSet("main") } }
        val only = sourceSetId(":a", "main")
        val request = fixture.requestFor(listOf(only), everything = listOf(only), buildModel = bare)
        val codes = fixture.run(fixture.producerWith("cyclic"), request).single().diagnosticCodes()
        assertTrue("kotlin.dependencies-assumed" in codes)
        assertTrue("kotlin.cyclic-module-reference" !in codes)
    }

    @Test
    fun `the guess about dependencies is announced once however many source sets are analysed`() {
        val bare = model {
            module(":a") { sourceSet("main") }
            module(":b") { sourceSet("main") }
            module(":c") { sourceSet("main") }
        }
        val all = listOf(sourceSetId(":a", "main"), sourceSetId(":b", "main"), sourceSetId(":c", "main"))
        val request = fixture.requestFor(all, everything = all, buildModel = bare)
        val batches = fixture.run(fixture.producerWith("ok"), request)
        assertEquals(3, batches.size)
        val announcing = batches.filter { "kotlin.dependencies-assumed" in it.diagnosticCodes() }
        assertEquals(listOf(all.first().value), announcing.map { it.owner.key })
    }

    @Test
    fun `with dependency information a cycle is still reported`() {
        val request = fixture.requestFor(listOf(fixture.app))
        val codes = fixture.run(fixture.producerWith("cyclic"), request).single().diagnosticCodes()
        assertTrue("kotlin.cyclic-module-reference" in codes)
        assertTrue("kotlin.dependencies-assumed" !in codes)
    }
}
