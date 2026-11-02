package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.allRelationships
import io.github.krosstack.kscope.service.assertCompilerExact
import io.github.krosstack.kscope.service.danglingTargets
import io.github.krosstack.kscope.service.declared
import io.github.krosstack.kscope.service.edgesInto
import io.github.krosstack.kscope.service.generated
import io.github.krosstack.kscope.service.generatesEdges
import io.github.krosstack.kscope.service.symbolOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scans a JVM module whose proto file the Wire plugin compiles to Kotlin, and a module with Kotlin that builds the
 * messages with named arguments, reads their fields, matches on an enum and uses the adapter. The classes exist only as
 * stubs that the Wire synthesizer makes from the proto file, and the compiler binds the consumers to them.
 */
class WireIntegrationTest {
    private val sample = SampleWorkspace("wire")
    private val library = "app/src/main/kotlin/sample/wire/app/Library.kt"
    private val proto = "proto/src/main/proto/library.proto"
    private val pkg = "sample.wire.proto"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `constructor calls, named arguments, field reads, enum entries and the adapter bind with compiler evidence`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertEquals(0, scan.diagnostics.warnings, "${scan.diagnostics.entries}")
            val graph = service.graph()
            val book = listOf("Book.<init>", "Book.title", "Book.pages", "Book.genre", "Book.copy")
            (book + listOf("Genre.FICTION", "Genre.SCIENCE")).forEach {
                assertCompilerExact(graph.edgesInto(library, "$pkg.$it"), it)
            }
            assertCompilerExact(graph.edgesInto(library, "$pkg.Book.Companion.ADAPTER"), "ADAPTER")
            assertCompilerExact(graph.edgesInto(library, "$pkg.Shelf.books"), "a repeated field")
            assertCompilerExact(graph.edgesInto(library, "$pkg.Book.Publisher.name"), "a nested message")
        }
    }

    @Test
    fun `a message generates its class with convention evidence and the stub says where it came from`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val message = graph.declared("$pkg.Book", "proto.message")
            assertEquals(proto, message.declaration?.artifact?.path?.value)
            val generates = graph.generatesEdges(message).single()
            assertEquals(EvidenceClass.CONVENTION, generates.evidenceClass)
            assertEquals(Certainty.EXACT, (generates.resolution as Resolution.Resolved).certainty)
            val generatedClass = assertNotNull(graph.symbolOf(generates))
            assertEquals("kotlin.class", generatedClass.kind.id)
            val origin = generatedClass.origin as SymbolOrigin.Generated
            assertEquals("wire", origin.generator)
            assertEquals(NodeRef.OfSymbol(message.id), origin.sourceOfTruth)
            val field = graph.declared("$pkg.Book.authors", "proto.field")
            assertEquals("kotlin.property", graph.symbolOf(graph.generatesEdges(field).single())?.kind?.id)
        }
    }

    @Test
    fun `every resolved target in the workspace names a symbol, the generated ones included`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            assertEquals(emptyList(), graph.danglingTargets())
            assertTrue(graph.allRelationships().any { graph.symbolOf(it)?.origin is SymbolOrigin.Generated })
        }
    }

    @Test
    fun `a new field of the message is a new property of the stub`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val field = "repeated string authors = 4;"
            val current = sample.root.resolve(proto).toFile().readText()
            sample.write(proto, current.replace(field, "$field\n  string isbn = 6;"))
            val verification = assertNotNull(service.scan(verify = true).verification)
            assertTrue(verification.matches, "${verification.differences}")
            assertEquals(1, service.graph().generated("$pkg.Book.isbn").size)
        }
    }
}
