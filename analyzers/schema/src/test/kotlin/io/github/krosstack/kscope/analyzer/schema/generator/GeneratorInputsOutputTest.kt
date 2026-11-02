package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.spi.ProduceRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorInputsOutputTest {
    @Test
    fun `no path in the facts or the sources is a location on disk`() = tasktide().use {
        val synthesis = it.synthesize()
        val text = synthesis.facts.toString() + synthesis.sources.toString()

        it.roots.values.forEach { root ->
            assertTrue(root.toString() !in text, "$root in the output")
            assertTrue(root.toString().replace('\\', '/') !in text, "$root in the output")
        }
        assertTrue(synthesis.facts.isNotEmpty() && synthesis.sources.isNotEmpty())
    }

    @Test
    fun `the same workspace gives the same facts and sources`() {
        val first = tasktide().use { it.synthesize().let { synthesis -> synthesis.facts to synthesis.sources } }
        val second = tasktide().use { it.synthesize().let { synthesis -> synthesis.facts to synthesis.sources } }

        assertEquals(first.first, second.first)
        assertEquals(first.second, second.second)
    }

    @Test
    fun `a workspace whose scripts give no input gets an empty batch and no class`() = tasktide(
        client = "plugins { kotlin(\"jvm\") }\n// openApiGenerate { inputSpec.set(\"a\") }\n",
        server = "val wire = \"openApiGenerate\"\n",
    ).use {
        val synthesis = it.synthesize()

        assertEquals(listOf(emptyList()), synthesis.batches.map { batch -> batch.facts })
        assertEquals(emptyList(), synthesis.sources)
    }

    @Test
    fun `an input of a script that no module contains is noted`() = GeneratorWorkspace(
        listOf(
            RepositorySpec(
                "stray",
                mapOf("loose/build.gradle.kts" to "wire { sourcePath(\"a\") }\n"),
                listOf(ModuleSpec(":other")),
            ),
        ),
    ).use {
        val synthesis = it.synthesize()

        assertEquals(listOf("generator.module-unknown"), synthesis.diagnostics().map { note -> note.code.value })
        assertEquals(emptyList(), synthesis.inputs("wire.source-path"))
    }

    @Test
    fun `a request with no unit gets no batch`() = tasktide().use {
        val request = ProduceRequest(emptyList(), it.model, it.files, emptyList())
        val batches = mutableListOf<Any>()

        assertEquals(emptyList(), GeneratorInputSynthesizer().synthesize(request) { batch -> batches += batch })
        assertEquals(emptyList(), batches)
    }

    @Test
    fun `the synthesizer consumes the build scripts, the proto files and the OpenAPI documents of the workspace`() {
        val consumed = GeneratorInputSynthesizer().descriptor.consumes.map { type -> type.id }.toSet()

        assertEquals(setOf("gradle.build-script", "proto.source", "openapi.document"), consumed)
    }
}
