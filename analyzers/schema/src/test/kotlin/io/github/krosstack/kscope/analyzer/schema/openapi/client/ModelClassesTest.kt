package io.github.krosstack.kscope.analyzer.schema.openapi.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelClassesTest {
    @Test
    fun `the types of the schemas are the Kotlin types the generator uses`() {
        val files = filesOf(
            schemas(
                "Mixed:\n  properties:\n" +
                    "    s: {type: string, format: date-time}\n" +
                    "    i: {type: integer}\n" +
                    "    l: {type: integer, format: int64}\n" +
                    "    n: {type: number}\n" +
                    "    f: {type: number, format: float}\n" +
                    "    d: {type: number, format: double}\n" +
                    "    b: {type: boolean}\n" +
                    "    a: {type: array, items: {type: boolean}}\n" +
                    "    r: {\$ref: \"#/components/schemas/Mixed\"}\n" +
                    "    e: {\$ref: \"#/components/schemas/Color\"}\n" +
                    "    o: {type: object}\n" +
                    "    m: {}",
                "Color:\n  type: string\n  enum: [red, green]",
            ),
        )

        assertEquals(
            listOf(
                "s: kotlin.String? = null",
                "i: kotlin.Int? = null",
                "l: kotlin.Long? = null",
                "n: java.math.BigDecimal? = null",
                "f: kotlin.Float? = null",
                "d: kotlin.Double? = null",
                "b: kotlin.Boolean? = null",
                "a: kotlin.collections.List<kotlin.Boolean>? = null",
                "r: sample.api.models.Mixed? = null",
                "e: kotlin.Any? = null",
                "o: kotlin.Any? = null",
                "m: kotlin.Any? = null",
            ),
            files.modelClasses().single().constructor.orEmpty().map { it.written() },
        )
    }

    @Test
    fun `a model has the required properties first, in camel case, once`() {
        val files = filesOf(
            schemas(
                "Task:\n  required: [due_date]\n  properties:\n" +
                    "    title: {type: string}\n    due_date: {type: string}\n    dueDate: {type: string}",
            ),
        )

        assertEquals(
            listOf("dueDate: kotlin.String", "title: kotlin.String? = null"),
            files.modelClasses().single().constructor.orEmpty().map { it.written() },
        )
    }

    @Test
    fun `enumerations, compositions, arrays and objects with no properties get no class`() {
        val files = filesOf(
            schemas(
                "Color:\n  type: string\n  enum: [red]",
                "Both:\n  allOf:\n    - {type: object}\n  properties:\n    a: {type: string}",
                "Names:\n  type: array\n  items: {type: string}",
                "Bare:\n  type: object",
                "Real:\n  properties:\n    a: {type: string}",
            ),
        )

        assertEquals(listOf("Real"), files.modelClasses().map { it.name })
        assertNull(files.apis)
    }
}
