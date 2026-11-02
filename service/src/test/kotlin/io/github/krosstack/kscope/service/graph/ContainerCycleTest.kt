package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.declared
import io.github.krosstack.kscope.service.neighbourhood
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Timeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A container chain that loops must not stop a question about the nesting of a symbol from ending. */
@Timeout(120)
class ContainerCycleTest {
    private val sample = SampleWorkspace("wire")
    private val pkg = "sample.wire.proto"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `the neighbourhood of an enum constant ends and nothing is nested in it`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val genre = service.graph().declared("$pkg.Genre", "proto.enum")
            val constant = service.graph().declared("$pkg.Genre.FICTION", "proto.enum-constant")

            assertEquals(emptyList(), service.neighbourhood(constant.id.value).declared)
            assertEquals(
                setOf("$pkg.Genre.GENRE_UNSPECIFIED", "$pkg.Genre.FICTION", "$pkg.Genre.SCIENCE"),
                service.neighbourhood(genre.id.value).declared.map { it.qualifiedName }.toSet(),
            )
        }
    }

    @Test
    fun `a symbol that is its own container has nothing nested in it`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val template = service.graph().declared("$pkg.Genre.FICTION", "proto.enum-constant")
            val loop = template.copy(container = template.id)

            assertEquals(emptyList(), lookupsOver(service.graph(), loop).descendants(loop.id))
        }
    }

    @Test
    fun `symbols that contain each other are each listed once`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val template = service.graph().declared("$pkg.Genre.FICTION", "proto.enum-constant")
            val first = template.copy(id = SymbolId("ring/first"), container = SymbolId("ring/second"))
            val second = template.copy(id = SymbolId("ring/second"), container = SymbolId("ring/first"))

            val nested = lookupsOver(service.graph(), first, second).descendants(first.id)

            assertEquals(listOf(second.id), nested.map { it.id })
        }
    }

    private fun lookupsOver(graph: GraphReader, vararg symbols: Symbol) =
        PointLookups(ReadBudget(MaterializedGraph(graph, emptyList(), symbols.toList(), emptyList())))

    /** Fails a walk that keeps reading, so that a loop shows as a failure and not as a hang that fills the heap. */
    private class ReadBudget(private val graph: GraphReader) : GraphReader by graph {
        private var reads = 0

        override fun symbols(query: SymbolQuery): List<Symbol> {
            check(++reads <= MAX_READS) { "The walk read $MAX_READS times and does not end" }
            return graph.symbols(query)
        }

        private companion object {
            const val MAX_READS = 50
        }
    }
}
