package io.github.krosstack.kscope.analyzer.schema.openapi.client

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiClassesTest {
    @Test
    fun `a class is made for each tag and DefaultApi for no tag, and an operation with several is in each`() {
        val files = filesOf(
            operations(
                "get:\n  operationId: a\n  tags: [Pets, Task Lists]",
                "put:\n  operationId: b",
                "post:\n  operationId: c\n  tags: [pets]",
            ),
        )

        assertEquals(listOf("DefaultApi", "PetsApi", "TaskListsApi"), files.apiClasses().map { it.name })
        assertEquals(
            listOf(listOf("b"), listOf("a", "c"), listOf("a")),
            files.apiClasses().map { api -> api.functions().map { it.name } },
        )
    }

    @Test
    fun `an operation with no operation id has no function, and a class with no function is not made`() {
        val files = filesOf(operations("get:\n  tags: [Pets]", "put:\n  operationId: b\n  tags: [Owners]"))

        assertEquals(listOf("OwnersApi"), files.apiClasses().map { it.name })
    }

    @Test
    fun `a function is named after its operation id in camel case`() {
        val files = filesOf(operations("get:\n  operationId: list_all-Tasks", "put:\n  operationId: GetOne"))

        assertEquals(listOf("listAllTasks", "getOne"), files.apiClasses().single().functions().map { it.name })
    }

    @Test
    fun `parameters are path, query and header parameters then the body, the required ones first`() {
        val files = filesOf(
            """
            openapi: 3.0.3
            paths:
              /things/{thingId}:
                put:
                  operationId: putThing
                  parameters:
                    - {name: thingId, in: path, required: true, schema: {type: integer, format: int64}}
                    - {name: dry_run, in: query, schema: {type: boolean}}
                    - {name: X-Trace, in: header, required: true, schema: {type: string}}
                    - {name: ratio, in: query, required: true, schema: {type: number}}
                  requestBody:
                    content:
                      application/json:
                        schema: {${'$'}ref: "#/components/schemas/Thing"}
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            type: array
                            items: {${'$'}ref: "#/components/schemas/Thing"}
            components:
              schemas:
                Thing:
                  properties:
                    name: {type: string}
            """.trimIndent(),
        )

        assertEquals(
            "putThing(thingId: kotlin.Long, xTrace: kotlin.String, ratio: java.math.BigDecimal, " +
                "dryRun: kotlin.Boolean? = null, thing: sample.api.models.Thing? = null): " +
                "kotlin.collections.List<sample.api.models.Thing>",
            files.apiClasses().single().functions().single().written(),
        )
    }

    @Test
    fun `a required body is not null, and a body that is no reference is called body`() {
        val files = filesOf(
            operations(
                "post:\n  operationId: a\n  requestBody:\n    required: true\n    content:\n      application/json:\n" +
                    "        schema: {type: array, items: {type: integer}}",
            ),
        )

        assertEquals(
            "a(body: kotlin.collections.List<kotlin.Int>): kotlin.Unit",
            files.apiClasses().single().functions().single().written(),
        )
    }

    @Test
    fun `the function returns the type of the first successful response with a JSON schema, or Unit`() {
        val files = filesOf(
            operations(
                "get:\n  operationId: first\n" + jsonResponse("{type: string}", "201"),
                "put:\n  operationId: second\n  responses:\n    \"204\": {description: none}",
                "post:\n  operationId: third\n" + jsonResponse("{type: string}", "404"),
            ),
        )

        assertEquals(
            listOf("kotlin.String", "kotlin.Unit", "kotlin.Unit"),
            files.apiClasses().single().functions().map { it.returnType },
        )
    }
}
