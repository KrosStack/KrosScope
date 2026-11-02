package io.github.krosstack.kscope.analyzer.schema.openapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val string = OpenApiType.Scalar("string", null)
private val book = OpenApiType.Reference("Book")

private val openApi3 = """
    openapi: 3.0.3
    paths:
      /books/{bookId}:
        parameters:
          - name: bookId
            in: path
            schema: {type: integer, format: int64}
          - ${'$'}ref: "#/components/parameters/Verbose"
        get:
          operationId: getBook
          tags: [Books, Shelf Items]
          parameters:
            - name: expand
              in: query
              schema:
                type: array
                items: {type: string}
            - name: X-Trace
              in: header
              required: true
              schema: {type: string}
            - name: session
              in: cookie
              schema: {type: string}
            - ${'$'}ref: "#/components/schemas/NotAParameter"
          responses:
            "404":
              description: Missing.
              content:
                application/json:
                  schema: {type: string}
            "200":
              description: The book.
              content:
                application/xml:
                  schema: {type: string}
                application/json; charset=utf-8:
                  schema:
                    ${'$'}ref: "#/components/schemas/Book"
        put:
          operationId: putBook
          parameters:
            - name: bookId
              in: path
              required: true
              schema: {type: string}
          requestBody:
            required: true
            content:
              application/json:
                schema:
                  ${'$'}ref: "#/components/schemas/Book"
          responses:
            "204": {description: Replaced.}
        delete:
          operationId: deleteBook
          requestBody:
            content:
              application/json:
                schema: {type: object}
          responses:
            "2XX":
              description: Gone.
              content:
                application/json:
                  schema: {type: boolean}
    components:
      parameters:
        Verbose:
          name: verbose
          in: query
          schema: {type: boolean}
""".trimIndent()

private val swagger2 = """
    swagger: "2.0"
    paths:
      /pets:
        post:
          operationId: addPet
          parameters:
            - name: pet
              in: body
              required: true
              schema:
                ${'$'}ref: "#/definitions/Pet"
            - name: limit
              in: query
              type: integer
              format: int32
            - name: form
              in: formData
              type: string
          responses:
            "201":
              description: Created.
              schema:
                ${'$'}ref: "#/definitions/Pet"
""".trimIndent()

private fun operation(document: OpenApiDocument, id: String) = document.operations.single { it.operationId == id }

class OpenApiOperationReaderTest {
    private val document = documentOf(openApi3)

    @Test
    fun `the tags of an operation are those it lists, in order`() {
        assertEquals(listOf("Books", "Shelf Items"), operation(document, "getBook").tags)
        assertEquals(emptyList(), operation(document, "putBook").tags)
    }

    @Test
    fun `the parameters of the path come first, those of the operation follow, and cookies are left out`() {
        assertEquals(
            listOf(
                OpenApiParameter("bookId", "path", true, OpenApiType.Scalar("integer", "int64")),
                OpenApiParameter("verbose", "query", false, OpenApiType.Scalar("boolean", null)),
                OpenApiParameter("expand", "query", false, OpenApiType.ListOf(string)),
                OpenApiParameter("X-Trace", "header", true, string),
            ),
            operation(document, "getBook").parameters,
        )
    }

    @Test
    fun `a parameter of the operation replaces the one of the path with its name and place`() {
        assertEquals(
            listOf(
                OpenApiParameter("verbose", "query", false, OpenApiType.Scalar("boolean", null)),
                OpenApiParameter("bookId", "path", true, string),
            ),
            operation(document, "putBook").parameters,
        )
    }

    @Test
    fun `a reference to anything but the parameters of the document gives no parameter`() {
        assertEquals(4, operation(document, "getBook").parameters.size)
    }

    @Test
    fun `the body is the schema of the first JSON media type, and it is required when the document says so`() {
        assertEquals(OpenApiBody(book, true), operation(document, "putBook").body)
        assertEquals(OpenApiBody(OpenApiType.Unknown, false), operation(document, "deleteBook").body)
        assertNull(operation(document, "getBook").body)
    }

    @Test
    fun `the response is the JSON schema of the first successful code that has one`() {
        assertEquals(book, operation(document, "getBook").response)
        assertNull(operation(document, "putBook").response)
        assertEquals(OpenApiType.Scalar("boolean", null), operation(document, "deleteBook").response)
    }

    @Test
    fun `a Swagger 2 document gives its body as a parameter and its response as a schema`() {
        val add = operation(documentOf(swagger2), "addPet")

        assertEquals(OpenApiBody(OpenApiType.Reference("Pet"), true), add.body)
        assertEquals(
            listOf(OpenApiParameter("limit", "query", false, OpenApiType.Scalar("integer", "int32"))),
            add.parameters,
        )
        assertEquals(OpenApiType.Reference("Pet"), add.response)
    }
}
