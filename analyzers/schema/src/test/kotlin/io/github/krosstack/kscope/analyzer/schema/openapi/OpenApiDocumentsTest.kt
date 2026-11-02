package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.Resources
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiDocumentsTest {
    @Test
    fun `a path that is a reference, an extension key and text of a block scalar are no operations`() {
        val names = libraryWorkspace().openApiSymbols().map { it.name }

        assertEquals(listOf("Library API", "listBooks", "POST /books", "getBook", "deleteBook", "Book", "Shelf"), names)
    }

    @Test
    fun `every symbol is exact and structural, and an operation is a contract`() {
        val all = libraryWorkspace().openApiSymbols()

        assertTrue(all.all { it.evidenceClass.name == "STRUCTURAL" && it.certainty.name == "EXACT" })
        assertEquals(setOf("SCHEMA", "CONTRACT"), all.map { it.kind.category.name }.toSet())
        assertEquals("CONTRACT", all.single { it.name == "listBooks" }.kind.category.name)
    }

    @Test
    fun `a Swagger JSON document gets its title from the file name and its servers from the base path`() {
        val swagger = TestWorkspace(
            mapOf("spec/pets.json" to Resources.text("openapi/pets.json")),
            listOf(ModuleSpec(":spec")),
        )
        val all = swagger.openApiSymbols()

        assertEquals("openapi.document", swagger.artifact("spec/pets.json").type.id)
        assertEquals(listOf("pets.json", "getPet", "PUT /pets/{petId}", "Pet"), all.map { it.name })
        assertEquals("swagger 2.0", all.first().signature)
        assertEquals("/v1", all.first().attributes["openapi.servers"])
        assertEquals(listOf(7, 10, 16), all.drop(1).map { it.line() })
        assertEquals("spec/pets.json#/definitions/Pet", all.last().qualifiedName)
    }

    @Test
    fun `a document that no module contains is noted and yields no symbols`() {
        val stray = TestWorkspace(
            mapOf("elsewhere/api.yaml" to "openapi: 3.0.0\npaths: {}\n"),
            listOf(ModuleSpec(":spec")),
        )
        val facts = stray.produce(OpenApiProducer()).facts()
        val note = facts.filterIsInstance<Fact.OfDiagnostic>().single().value

        assertEquals(emptyList(), facts.symbols())
        assertEquals("openapi.module-unknown", note.code.value)
        assertEquals(DiagnosticSeverity.INFO, note.severity)
    }

    @Test
    fun `a document with no paths is the document symbol alone`() {
        val source = "openapi: 3.1.0\ninfo:\n  title: Bare\n"
        val bare = TestWorkspace(mapOf("spec/api.yml" to source), listOf(ModuleSpec(":spec")))
        val all = bare.openApiSymbols()

        assertEquals(listOf("Bare"), all.map { it.name })
        assertNotNull(all.single().nameLocation)
    }
}
