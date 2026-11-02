package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.proto.ProtoProducer
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoContainerTest {
    private val path = "lib/src/main/proto/library.proto"

    private fun containers(source: String): Map<String, String?> {
        val facts = TestWorkspace(mapOf(path to source), listOf(ModuleSpec(":lib"))).produce(ProtoProducer()).facts()
        val symbols = facts.symbols()
        val names = symbols.associate { it.id to requireNotNull(it.qualifiedName) }
        return symbols.associate { requireNotNull(it.qualifiedName) to it.container?.let(names::getValue) }
    }

    @Test
    fun `a member is contained by the message, enum or service that declares it`() {
        val book = "sample.library.Book"
        val expected = mapOf(
            book to null,
            "$book.lending" to book,
            "$book.title" to book,
            "$book.borrower" to book,
            "$book.Publisher" to book,
            "$book.Publisher.name" to "$book.Publisher",
            "sample.library.Genre" to null,
            "sample.library.Genre.GENRE_UNSPECIFIED" to "sample.library.Genre",
            "sample.library.Genre.FICTION" to "sample.library.Genre",
            "sample.library.Catalog" to null,
            "sample.library.Catalog.Find" to "sample.library.Catalog",
        )
        val actual = containers(Resources.text("wire/library.proto"))
        assertEquals(expected, expected.keys.associateWith { actual[it] })
    }

    @Test
    fun `an enum and a oneof nested in a message are contained by that message`() {
        val source = """
            package sample.nested;
            message Outer {
              enum Mode { MODE_UNSPECIFIED = 0; }
              oneof pick { string label = 1; }
            }
        """.trimIndent()
        assertEquals(
            mapOf(
                path to null,
                "sample.nested.Outer" to null,
                "sample.nested.Outer.pick" to "sample.nested.Outer",
                "sample.nested.Outer.label" to "sample.nested.Outer",
                "sample.nested.Outer.Mode" to "sample.nested.Outer",
                "sample.nested.Outer.Mode.MODE_UNSPECIFIED" to "sample.nested.Outer.Mode",
            ),
            containers(source),
        )
    }

    @Test
    fun `no symbol is its own container`() {
        val ownContainers = containers(Resources.text("wire/library.proto")).filter { (name, container) ->
            name == container
        }
        assertEquals(emptyMap(), ownContainers)
    }
}
