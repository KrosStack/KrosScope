package io.github.krosstack.kscope.analyzer.schema.openapi.client

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.openapi.documentOf
import io.github.krosstack.kscope.analyzer.schema.stub.StubFile
import io.github.krosstack.kscope.analyzer.schema.stub.StubSymbols
import io.github.krosstack.kscope.analyzer.schema.stub.StubText
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenApiClientPlanTest {
    @Test
    fun `the spec of the fixture gives TasksApi with listTasks and the data class Task`() {
        val files = filesOf(TASKS_SPEC)

        assertEquals(
            "package sample.api.apis\n\n" +
                "public class TasksApi {\n" +
                "    public fun listTasks(): kotlin.collections.List<sample.api.models.Task> = TODO()\n" +
                "}\n",
            StubText.render(checkNotNull(files.apis)),
        )
        assertEquals(
            "package sample.api.models\n\n" +
                "public data class Task(\n" +
                "    public val id: kotlin.String,\n" +
                "    public val title: kotlin.String,\n" +
                ")\n",
            StubText.render(checkNotNull(files.models)),
        )
    }

    @Test
    fun `a document with nothing for a client has no files`() {
        val files = filesOf("openapi: 3.0.3\npaths: {}\n")

        assertNull(files.apis)
        assertNull(files.models)
    }

    @Test
    fun `a Swagger 2 document gives its definitions and its body parameter`() {
        val files = filesOf(
            """
            swagger: "2.0"
            paths:
              /pets:
                post:
                  operationId: addPet
                  tags: [pets]
                  parameters:
                    - {name: pet, in: body, required: true, schema: {${'$'}ref: "#/definitions/Pet"}}
                  responses:
                    "201":
                      schema: {${'$'}ref: "#/definitions/Pet"}
            definitions:
              Pet:
                required: [name]
                properties:
                  name: {type: string}
            """.trimIndent(),
        )

        assertEquals(
            "addPet(pet: sample.api.models.Pet): sample.api.models.Pet",
            files.apiClasses().single().functions().single().written(),
        )
        assertEquals(
            listOf("name: kotlin.String"),
            files.modelClasses().single().constructor.orEmpty().map { it.written() },
        )
    }

    @Test
    fun `the function and the class are generated from their operation and schema, their members only name them`() {
        val files = filesOf(TASKS_SPEC)
        val home = IdentityFactory(ModuleId("app//:client"), "main")
        val facts = listOfNotNull(files.apis, files.models).map {
            StubSymbols(home, "openapi-generator", Scope()).of(it)
        }
        val symbols = facts.flatMap { it.symbols }
        val schema = ids.schemaId(documentOf(TASKS_SPEC).schemas.single())

        assertEquals(
            listOf(
                "GENERATES/ :@main:openapi/`openapi/api.yaml`:`GET /tasks`. -> " +
                    ":client@main:sample/api/apis/TasksApi#listTasks(). CONVENTION",
                "GENERATES/ :@main:openapi/`openapi/api.yaml`:Task# -> :client@main:sample/api/models/Task# CONVENTION",
            ),
            facts.flatMap { it.relationships }.map { Render.relationship(it) },
        )
        val function = symbols.single { it.kind.id == "kotlin.function" }
        assertEquals(schema.value, function.attributes["generated.type-source"])
        val title = symbols.single { it.qualifiedName == "sample.api.models.Task.title" }
        assertEquals(NodeRef.OfSymbol(schema), (title.origin as SymbolOrigin.Generated).sourceOfTruth)
    }

    @Test
    fun `the packages of the files are the ones the client is made in`() {
        val files = filesOf(TASKS_SPEC)

        assertEquals("sample.api.apis", files.apis?.packageName)
        assertEquals("sample.api.models", files.models?.packageName)
        assertEquals(listOf("TasksApi"), files.apis.classNames())
        assertEquals(listOf("Task"), files.models.classNames())
    }

    private fun StubFile?.classNames(): List<String> = this?.classes.orEmpty().map { it.name }
}
