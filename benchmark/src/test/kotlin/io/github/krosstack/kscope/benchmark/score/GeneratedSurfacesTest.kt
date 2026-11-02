package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedSurfacesTest {
    private val sourceKinds = setOf("proto.message")
    private val message = Graphs.symbol("api.proto", "p.Message", "proto.message", 1)
    private val generated = Graphs.symbol("Gen.kt", "p.Gen", "kotlin.class", 2)
    private val member = Graphs.symbol("Gen.kt", "p.Gen.value", "kotlin.property", 3)
    private val generates = Graphs.call(message, generated, "api.proto", 1, RelationKind.GENERATES)

    private fun sourcesOf(target: Symbol, vararg symbols: Symbol): List<SymbolId> {
        val graph = FakeGraph(symbols = symbols.toList(), relationships = listOf(generates))
        return GeneratedSurfaces(GraphIndex(graph)).typeSourcesOf(target, sourceKinds).map { it.source.id }
    }

    @Test
    fun `a member of a generated type has the sources of that type`() {
        val inside = member.copy(container = generated.id)
        assertEquals(listOf(message.id), sourcesOf(inside, message, generated, inside))
    }

    @Test
    @Timeout(10)
    fun `a symbol that is its own container has no sources and the search ends`() {
        val loop = member.copy(container = member.id)
        assertEquals(emptyList(), sourcesOf(loop, message, generated, loop))
    }
}
