package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PROTO_FROM = ":@main:proto/sample/tasktide/sync/v1/"
private const val DOCUMENT = "openapi/`openapi/tasktide.yaml`:"

private fun Synthesis.edges(kind: RelationKind): List<String> =
    facts.relationships().filter { it.kind == kind }.map { Render.relationship(it) }

private fun Synthesis.sourcesBy(generator: String): List<SyntheticSource> = sources.filter { it.generator == generator }

class GeneratorStubsTest {
    @Test
    fun `Wire classes are declared in each consumer and generated from the proto symbols of the other repository`() =
        tasktide().use {
            val synthesis = it.synthesize()
            val sources = synthesis.sourcesBy("wire")
            val edges = synthesis.edges(RelationKind.GENERATES)

            assertEquals(listOf("app//:client@main", "app//:server@main"), sources.map { s -> s.sourceSet.value })
            assertEquals(2, sources.map { source -> source.sourceSet to source.virtualPath }.toSet().size)
            assertTrue(sources.all { source -> source.virtualPath.startsWith("wire-input/") })
            assertTrue(sources.all { source -> source.language == "kotlin" })
            assertTrue(sources.all { source -> "public class PushRequest" in source.text })
            listOf(":client", ":server").forEach { module ->
                val generated = "$module@main:sample/tasktide/sync/v1"
                val request = "GENERATES/ ${PROTO_FROM}PushRequest# -> $generated/PushRequest#"
                val reply = "GENERATES/ ${PROTO_FROM}PushReply# -> $generated/PushReply#"
                assertTrue("$request CONVENTION" in edges, request)
                assertTrue("$reply CONVENTION" in edges, reply)
            }
        }

    @Test
    fun `a generated Wire symbol is in the source set of its consumer and names its proto symbol as its source`() =
        tasktide().use {
            val field = it.synthesize().facts.symbols().filter { symbol ->
                symbol.qualifiedName == "sample.tasktide.sync.v1.PushRequest.device_id"
            }
            val origins = field.map { symbol -> symbol.origin as SymbolOrigin.Generated }

            assertEquals(2, field.size)
            assertEquals(
                listOf("app//:client@main", "app//:server@main"),
                field.mapNotNull { symbol -> symbol.scope.sourceSet?.value }.sorted(),
            )
            assertEquals(listOf("wire", "wire"), origins.map { origin -> origin.generator })
            assertTrue(
                origins.all { origin ->
                    Render.node(assertNotNull(origin.sourceOfTruth)) == "${PROTO_FROM}PushRequest#device_id."
                },
            )
        }

    @Test
    fun `the OpenAPI client is declared in the consumer and generated from the operations and schemas of the spec`() =
        tasktide().use {
            val synthesis = it.synthesize()
            val sources = synthesis.sourcesBy("openapi-generator")
            val edges = synthesis.edges(RelationKind.GENERATES)
            val operation = "GENERATES/ :@main:$DOCUMENT`GET /tasks`. -> "
            val schema = "GENERATES/ :@main:${DOCUMENT}Task# -> "

            assertEquals(2, sources.size)
            assertTrue(sources.all { source -> source.sourceSet.value == "app//:client@main" })
            assertTrue(sources.all { source -> source.virtualPath.startsWith("openapi-client/") })
            assertTrue(sources.any { source -> "public class TasksApi" in source.text })
            assertTrue(sources.any { source -> "package sample.tasktide.api.models" in source.text })
            val function = "$operation:client@main:sample/tasktide/api/apis/TasksApi#listTasks(). CONVENTION"
            assertTrue(function in edges, function)
            val model = "$schema:client@main:sample/tasktide/api/models/Task# CONVENTION"
            assertTrue(model in edges, model)
        }

    @Test
    fun `a generator other than kotlin is noted, its links are made and it makes no class`() = tasktide(
        client = CLIENT_SCRIPT.replace("generatorName.set(\"kotlin\")", "generatorName.set(\"kotlin-spring\")"),
    ).use {
        val synthesis = it.synthesize()
        val notes = synthesis.diagnostics()

        assertEquals(listOf("openapi-generator.generator-not-supported"), notes.map { note -> note.code.value })
        assertEquals("client/build.gradle.kts", notes.single().location?.artifact?.path?.value)
        assertEquals(3, synthesis.dependencies().size)
        assertEquals(emptyList(), synthesis.sourcesBy("openapi-generator"))
        assertEquals("kotlin-spring", synthesis.inputs("openapi-generator.input").single().attributes.values.single())
    }

    @Test
    fun `packages that are computed are noted, and unset ones are the defaults of the generator`() {
        tasktide(client = CLIENT_SCRIPT.replace("\"sample.tasktide.api\"", "base")).use {
            val synthesis = it.synthesize()

            assertEquals(listOf("openapi-generator.package-unknown"), synthesis.diagnostics().map { n -> n.code.value })
            assertEquals(emptyList(), synthesis.sourcesBy("openapi-generator"))
        }
        tasktide(client = CLIENT_SCRIPT.replace("    packageName.set(\"sample.tasktide.api\")\n", "")).use {
            val sources = it.synthesize().sourcesBy("openapi-generator")

            assertTrue(sources.any { source -> "package org.openapitools.client.apis" in source.text })
            assertTrue(sources.any { source -> "package org.openapitools.client.models" in source.text })
        }
    }

    @Test
    fun `a module that does not have Wire generate Kotlin has its links and no Wire class`() = tasktide(
        clientModule = ModuleSpec(":client"),
        serverModule = ModuleSpec(":server"),
    ).use {
        val synthesis = it.synthesize()

        assertEquals(3, synthesis.dependencies().size)
        assertEquals(emptyList(), synthesis.sourcesBy("wire"))
        assertEquals(2, synthesis.sourcesBy("openapi-generator").size)
        assertEquals(emptyList(), synthesis.diagnostics())
    }

    @Test
    fun `a module with no main or commonMain source set is noted and gets no class`() = tasktide(
        clientModule = ModuleSpec(":client", sourceSets = emptyList(), attributes = wireKotlin),
    ).use {
        val synthesis = it.synthesize()

        assertEquals(
            listOf("openapi-generator.no-source-set", "wire.no-source-set"),
            synthesis.diagnostics().map { note -> note.code.value }.sorted(),
        )
        assertEquals(listOf("app//:server@main"), synthesis.sources.map { source -> source.sourceSet.value })
        assertEquals(3, synthesis.dependencies().size)
    }

    @Test
    fun `commonMain is where the classes go when a module has no main source set`() = tasktide(
        clientModule = ModuleSpec(":client", sourceSets = listOf("commonMain", "jvmMain"), attributes = wireKotlin),
    ).use {
        val sources = it.synthesize().sources.filter { source -> "client" in source.sourceSet.value }

        assertEquals(3, sources.size)
        assertEquals(setOf("app//:client@commonMain"), sources.map { source -> source.sourceSet.value }.toSet())
    }

    @Test
    fun `a proto file in a source set of the consumer is linked and left to the Wire synthesizer of its module`() =
        tasktide(
            client = CLIENT_SCRIPT.replace("\$rootDir/../spec/proto", "src/main/proto"),
            appFiles = mapOf("client/src/main/proto/own.proto" to SYNC_PROTO),
        ).use {
            val synthesis = it.synthesize()
            val wire = synthesis.sourcesBy("wire")

            assertEquals(3, synthesis.dependencies().size)
            assertEquals(listOf("app//:server@main"), wire.map { source -> source.sourceSet.value })
        }
}
