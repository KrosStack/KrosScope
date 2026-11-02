package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiProducerTest {
    private val workspace = libraryWorkspace()

    private fun symbols(): List<Symbol> = workspace.openApiSymbols()

    private fun named(name: String): Symbol = symbols().single { it.name == name }

    @Test
    fun `a YAML document with a top-level openapi key is an OpenAPI document`() {
        assertEquals("openapi.document", workspace.artifact(LIBRARY_PATH).type.id)
    }

    @Test
    fun `the document symbol is named by its title and sits at the openapi key`() {
        val document = symbols().first()

        assertEquals("openapi.document", document.kind.id)
        assertEquals("Library API", document.name)
        assertEquals(LIBRARY_PATH, document.qualifiedName)
        assertEquals("openapi 3.0.3", document.signature)
        assertEquals(1, document.line())
        assertEquals(1 to 1, document.lines())
        assertEquals(mapOf("openapi.version" to "3.0.3", "openapi.servers" to "/api/v2 /v2"), document.attributes)
        assertEquals(null, document.container)
    }

    @Test
    fun `an operation is named by its operationId, or by its method and path, and sits at that line`() {
        val operations = symbols().filter { it.kind.id == "openapi.operation" }

        assertEquals(
            listOf("listBooks" to 11, "POST /books" to 16, "getBook" to 31, "deleteBook" to 39),
            operations.map { it.name to it.line() },
        )
    }

    @Test
    fun `an operation keeps its method and path, its signature and the lines of its block`() {
        val operations = symbols().filter { it.kind.id == "openapi.operation" }

        assertEquals(
            listOf("GET /books", "POST /books", "GET /books/{bookId}", "DELETE /books/{bookId}"),
            operations.map { it.signature },
        )
        assertEquals(
            listOf("GET" to "/books", "POST" to "/books", "GET" to "/books/{bookId}", "DELETE" to "/books/{bookId}"),
            operations.map { it.attributes["openapi.method"] to it.attributes["openapi.path"] },
        )
        assertEquals(listOf(10 to 15, 16 to 22, 30 to 37, 38 to 39), operations.map { it.lines() })
        assertEquals("listBooks", named("listBooks").attributes["openapi.operation-id"])
        assertEquals(null, named("POST /books").attributes["openapi.operation-id"])
    }

    @Test
    fun `qualified names are the file and the JSON pointer of the element`() {
        val names = symbols().map { it.qualifiedName }

        assertTrue("$LIBRARY_PATH#/paths/~1books/get" in names, "$names")
        assertTrue("$LIBRARY_PATH#/paths/~1books~1{bookId}/delete" in names, "$names")
        assertTrue("$LIBRARY_PATH#/components/schemas/Book" in names, "$names")
    }

    @Test
    fun `a summary becomes the documentation of an operation`() {
        assertEquals("Lists the books.", named("listBooks").documentation)
        assertEquals("Adds a book.", named("POST /books").documentation)
        assertEquals(null, named("getBook").documentation)
    }

    @Test
    fun `an entry of components schemas is a schema symbol at its key`() {
        val schemas = symbols().filter { it.kind.id == "openapi.schema" }

        assertEquals(listOf("Book" to 46, "Shelf" to 51), schemas.map { it.name to it.line() })
        assertEquals(46 to 50, schemas.first().lines())
    }

    @Test
    fun `operations and schemas are contained by the document`() {
        val all = symbols()
        val document = all.first()

        assertTrue(all.drop(1).all { it.container == document.id }, "$all")
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun `symbol ids carry the module and the file`() {
        val ids = symbols().map { Render.id(it.id) }

        assertTrue(":spec@.:openapi/`$LIBRARY_PATH`:" in ids, "$ids")
        assertTrue(":spec@.:openapi/`$LIBRARY_PATH`:`GET /books`." in ids, "$ids")
        assertTrue(":spec@.:openapi/`$LIBRARY_PATH`:Book#" in ids, "$ids")
    }
}
