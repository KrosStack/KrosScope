package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiProducer
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoProducer
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun Symbol.moduleName(): String = Render.id(id).substringBefore('@')

private fun Resolution.hint(): String = (this as Resolution.Unresolved).hint

class GeneratorInputSynthesizerTest {
    @Test
    fun `the workspace unit gets one batch from the producer of the generator inputs`() = tasktide().use {
        val batch = it.synthesize().batches.single()

        assertEquals("kscope.schema.generator-inputs", batch.producer.id)
        assertEquals(OwnershipKind.WORKSPACE, batch.owner.kind)
        assertEquals("workspace", batch.owner.key)
    }

    @Test
    fun `each input is a symbol of the module of its script, at its call, with no source set`() = tasktide().use {
        val synthesis = it.synthesize()
        val spec = synthesis.inputs("openapi-generator.input").single()
        val wire = synthesis.inputs("wire.source-path").sortedBy { symbol -> symbol.moduleName() }

        assertEquals(":client", spec.moduleName())
        assertEquals("tasktide.yaml", spec.name)
        assertEquals("\$rootDir/../spec/openapi/tasktide.yaml", spec.signature)
        assertEquals("client/build.gradle.kts#openApiGenerate.inputSpec", spec.qualifiedName)
        assertEquals(lineOf(CLIENT_SCRIPT, "inputSpec"), spec.nameLocation?.range?.startLine)
        assertEquals("client/build.gradle.kts", spec.nameLocation?.artifact?.path?.value)
        assertEquals(mapOf("openapi-generator.name" to "kotlin"), spec.attributes)
        assertNull(spec.scope.sourceSet)
        assertEquals(listOf(":client", ":server"), wire.map { symbol -> symbol.moduleName() })
        assertEquals(listOf("proto", "proto"), wire.map { symbol -> symbol.name })
        assertEquals("client/build.gradle.kts#wire.sourcePath", wire.first().qualifiedName)
        assertEquals(lineOf(CLIENT_SCRIPT, "srcDir"), wire.first().nameLocation?.range?.startLine)
        assertEquals(lineOf(SERVER_SCRIPT, "srcDir"), wire.last().nameLocation?.range?.startLine)
        assertEquals(setOf("BUILD"), (wire + spec).map { symbol -> symbol.kind.category.name }.toSet())
    }

    @Test
    fun `the id of an input names the script, the property and the literal`() = tasktide().use {
        val spec = it.synthesize().inputs("openapi-generator.input").single()

        assertEquals(
            ":client@.:`client/build.gradle.kts`:`openApiGenerate.inputSpec`:" +
                "`\$rootDir/../spec/openapi/tasktide.yaml`.",
            Render.id(spec.id),
        )
    }

    @Test
    fun `an input depends on the spec file by the ids that the producers of the file make`() = tasktide().use {
        val document = it.produce(OpenApiProducer(), it.artifact("spec", "openapi/tasktide.yaml")).symbols()
            .single { symbol -> symbol.kind.id == "openapi.document" }
        val proto = it.artifact("spec", "proto/sample/tasktide/sync/v1/sync.proto")
        val file = it.produce(ProtoProducer(), proto).symbols().single { symbol -> symbol.kind.id == "proto.file" }
        val links = it.synthesize().dependencies()
        val targets = links.map { link -> (link.target() as NodeRef.OfSymbol).id }

        assertEquals(3, links.size)
        assertEquals(1, targets.count { id -> id == document.id })
        assertEquals(2, targets.count { id -> id == file.id })
        links.forEach { link ->
            assertEquals("generator-input", link.subKind)
            assertEquals(EvidenceClass.BUILD_MODEL, link.evidenceClass)
            assertEquals(Certainty.EXACT, (link.resolution as Resolution.Resolved).certainty)
            assertNull(link.scope.sourceSet)
        }
    }

    @Test
    fun `a dependency sits at the call of its input`() = tasktide().use {
        val synthesis = it.synthesize()
        val declarations = synthesis.facts.symbols().associate { symbol -> symbol.id to symbol.declaration }

        synthesis.dependencies().forEach { link ->
            assertEquals(declarations.getValue((link.from as NodeRef.OfSymbol).id), link.site)
        }
    }

    @Test
    fun `an input whose spec is in no repository of the workspace is one unresolved link`() = tasktide(
        client = CLIENT_SCRIPT.replace("../spec/openapi/tasktide.yaml", "../nowhere/api.yaml")
            .replace("../spec/proto", "../nowhere/proto"),
    ).use {
        val synthesis = it.synthesize()
        val resolutions = synthesis.dependencies().map { link -> link.resolution }
        val unresolved = resolutions.filterIsInstance<Resolution.Unresolved>()

        assertEquals(3, synthesis.inputs("openapi-generator.input").size + synthesis.inputs("wire.source-path").size)
        assertEquals(List(2) { "artifact.producer-not-in-workspace" }, unresolved.map { link -> link.reason.value })
        assertEquals(
            setOf("\$rootDir/../nowhere/api.yaml", "\$rootDir/../nowhere/proto"),
            unresolved.map { link -> link.hint() }.toSet(),
        )
        assertEquals(listOf("app//:server@main"), synthesis.sources.map { source -> source.sourceSet.value })
    }

    @Test
    fun `an input that is given twice is two symbols, and its classes are made once`() = tasktide(
        client = CLIENT_SCRIPT.replace(
            "srcDir(\"\$rootDir/../spec/proto\")",
            "srcDir(\"\$rootDir/../spec/proto\")\n        srcDir(\"\$rootDir/../spec/proto\")",
        ),
    ).use {
        val synthesis = it.synthesize()
        val inputs = synthesis.inputs("wire.source-path").filter { symbol -> symbol.moduleName() == ":client" }

        assertEquals(2, inputs.size)
        assertEquals(2, inputs.map { symbol -> symbol.id }.toSet().size)
        val wire = synthesis.sources.filter { source -> source.generator == "wire" }
        assertEquals(1, wire.count { source -> source.sourceSet.value == "app//:client@main" })
    }

    @Test
    fun `a Groovy build script gives its inputs as well`() = tasktide(
        client = "openApiGenerate {\n" +
            "    generatorName = 'kotlin'\n" +
            "    inputSpec = \"\$rootDir/../spec/openapi/tasktide.yaml\"\n" +
            "}\n" +
            "wire {\n    sourcePath { srcDir \"\${rootDir}/../spec/proto\" }\n    kotlin {}\n}\n",
    ).use {
        val synthesis = it.synthesize()

        assertEquals(1, synthesis.inputs("openapi-generator.input").size)
        assertEquals(2, synthesis.inputs("wire.source-path").size)
        assertEquals(3, synthesis.dependencies().count { link -> link.resolution is Resolution.Resolved })
    }
}
