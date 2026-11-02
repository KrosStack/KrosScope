package io.github.krosstack.kscope.analyzer.schema.openapi

import kotlin.test.Test
import kotlin.test.assertEquals

private val document = documentOf(
    """
    openapi: 3.0.3
    components:
      schemas:
        Task:
          type: object
          required: [id, title]
          properties:
            id: {type: string}
            title: {type: string}
            due_date: {type: string, format: date}
            size: {type: integer, format: int64}
            ratio: {type: number, format: double}
            done: {type: boolean}
            tags:
              type: array
              items: {type: string}
            owner:
              ${'$'}ref: "#/components/schemas/Person"
            extra: {type: object}
            odd: [a, b]
        Person:
          properties:
            name: {type: string}
        Status:
          type: string
          enum: [open, done]
        Pair:
          allOf:
            - ${'$'}ref: "#/components/schemas/Task"
          properties:
            more: {type: string}
        Anything:
          type: object
        Names:
          type: array
          items: {type: string}
    """.trimIndent(),
)

private fun propertiesOf(name: String) = document.schemas.single { it.name == name }.properties

class OpenApiSchemaReaderTest {
    @Test
    fun `an object schema lists its properties in order, with their types and whether they are required`() {
        assertEquals(
            listOf(
                OpenApiProperty("id", true, OpenApiType.Scalar("string", null)),
                OpenApiProperty("title", true, OpenApiType.Scalar("string", null)),
                OpenApiProperty("due_date", false, OpenApiType.Scalar("string", "date")),
                OpenApiProperty("size", false, OpenApiType.Scalar("integer", "int64")),
                OpenApiProperty("ratio", false, OpenApiType.Scalar("number", "double")),
                OpenApiProperty("done", false, OpenApiType.Scalar("boolean", null)),
                OpenApiProperty("tags", false, OpenApiType.ListOf(OpenApiType.Scalar("string", null))),
                OpenApiProperty("owner", false, OpenApiType.Reference("Person")),
                OpenApiProperty("extra", false, OpenApiType.Unknown),
                OpenApiProperty("odd", false, OpenApiType.Unknown),
            ),
            propertiesOf("Task"),
        )
    }

    @Test
    fun `a schema with properties and no type is an object as well`() {
        assertEquals(listOf("name"), propertiesOf("Person").map { it.name })
    }

    @Test
    fun `an enumeration, a composition, a bare object and an array have no properties`() {
        listOf("Status", "Pair", "Anything", "Names").forEach { assertEquals(emptyList(), propertiesOf(it), it) }
    }

    @Test
    fun `the schemas keep their names and their places in the file`() {
        assertEquals(
            listOf("Task", "Person", "Status", "Pair", "Anything", "Names"),
            document.schemas.map { it.name },
        )
        assertEquals(setOf("components/schemas"), document.schemas.map { it.section }.toSet())
    }
}
