package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.proto.ProtoProducer
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoProducerTest {
    private val path = "lib/src/main/proto/library.proto"
    private val workspace = TestWorkspace(
        mapOf(path to Resources.text("wire/library.proto")),
        listOf(ModuleSpec(":lib")),
    )

    private fun facts(): List<Fact> = workspace.produce(ProtoProducer()).facts()

    private fun Symbol.line() = requireNotNull(nameLocation).range.startLine

    @Test
    fun `a proto file is recognised by its extension`() {
        assertEquals("proto.source", workspace.artifact(path).type.id)
    }

    @Test
    fun `a file, its messages, fields, oneofs, enums, services and rpcs become schema symbols with qualified names`() {
        val described = facts().symbols().map { "${it.kind.id} ${it.qualifiedName}" }
        assertEquals(
            listOf(
                "proto.file $path",
                "proto.message sample.library.Book",
                "proto.oneof sample.library.Book.lending",
                "proto.field sample.library.Book.title",
                "proto.field sample.library.Book.pages",
                "proto.field sample.library.Book.genre",
                "proto.field sample.library.Book.authors",
                "proto.field sample.library.Book.stock",
                "proto.field sample.library.Book.subtitle",
                "proto.field sample.library.Book.publisher",
                "proto.field sample.library.Book.cover",
                "proto.field sample.library.Book.borrower",
                "proto.field sample.library.Book.due",
                "proto.field sample.library.Book.added",
                "proto.message sample.library.Book.Publisher",
                "proto.field sample.library.Book.Publisher.name",
                "proto.enum sample.library.Genre",
                "proto.enum-constant sample.library.Genre.GENRE_UNSPECIFIED",
                "proto.enum-constant sample.library.Genre.FICTION",
                "proto.service sample.library.Catalog",
                "proto.rpc sample.library.Catalog.Find",
                "proto.rpc sample.library.Catalog.Watch",
            ),
            described,
        )
    }

    @Test
    fun `every symbol is in the schema category with exact structural evidence`() {
        val symbols = facts().symbols()
        assertTrue(symbols.all { it.kind.category.name == "SCHEMA" })
        assertTrue(symbols.all { it.evidenceClass.name == "STRUCTURAL" && it.certainty.name == "EXACT" })
    }

    @Test
    fun `the name location of an element is the line of its name`() {
        val byName = facts().symbols().associateBy { it.qualifiedName }
        assertEquals(10, byName.getValue("sample.library.Book").line())
        assertEquals(11, byName.getValue("sample.library.Book.title").line())
        assertEquals(19, byName.getValue("sample.library.Book.lending").line())
        assertEquals(30, byName.getValue("sample.library.Genre").line())
        assertEquals(25, byName.getValue("sample.library.Book.Publisher").line())
        assertEquals(36, byName.getValue("sample.library.Catalog.Find").line())
    }

    @Test
    fun `the declaration of a message spans its block and a field spans its statement`() {
        val byName = facts().symbols().associateBy { it.qualifiedName }
        val book = requireNotNull(byName.getValue("sample.library.Book").declaration).range
        val added = requireNotNull(byName.getValue("sample.library.Book.added").declaration).range
        assertEquals(10 to 28, book.startLine to book.endLine)
        assertEquals(23 to 23, added.startLine to added.endLine)
    }

    @Test
    fun `documentation and field details are kept`() {
        val byName = facts().symbols().associateBy { it.qualifiedName }
        assertEquals("A book on a shelf.", byName.getValue("sample.library.Book").documentation)
        val stock = byName.getValue("sample.library.Book.stock")
        assertEquals("map<string, int64> stock = 5", stock.signature)
        assertEquals("int64", byName.getValue("sample.library.Book.due").attributes["proto.type"])
        assertEquals("lending", byName.getValue("sample.library.Book.due").attributes["proto.oneof"])
    }

    @Test
    fun `symbol ids carry the module, the source set and the proto package`() {
        val ids = facts().symbols().map { Render.id(it.id) }
        assertTrue(":lib@main:proto/sample/library/Book#title." in ids, "$ids")
        assertTrue(":lib@main:proto/sample/library/Book#Publisher#name." in ids, "$ids")
    }

    @Test
    fun `a syntax error is a warning and yields no symbols`() {
        val broken = TestWorkspace(
            mapOf("lib/src/main/proto/broken.proto" to "message {"),
            listOf(ModuleSpec(":lib")),
        )
        val facts = broken.produce(ProtoProducer()).facts()
        assertEquals(emptyList(), facts.symbols())
        val diagnostic = facts.filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertEquals("proto.syntax-error", diagnostic.code.value)
    }

    @Test
    fun `a file that no module contains is noted and yields no symbols`() {
        val stray = TestWorkspace(mapOf("elsewhere/x.proto" to "syntax = \"proto3\";"), listOf(ModuleSpec(":lib")))
        val facts = stray.produce(ProtoProducer()).facts()
        assertEquals(emptyList(), facts.symbols())
        assertEquals("proto.module-unknown", facts.filterIsInstance<Fact.OfDiagnostic>().single().value.code.value)
    }
}
