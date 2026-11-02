package io.github.krosstack.kscope.benchmark.score.code

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldClosedWorldFile
import io.github.krosstack.kscope.benchmark.gold.GoldEdge
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupertypeCallScoringTest {
    private val file = "core/src/main/kotlin/p/Caller.kt"
    private val target = "core/src/main/kotlin/p/Base.kt"

    private fun type(name: String) = SymbolDescriptor().namespace("p").type(name)

    private val caller = Graphs.symbol(file, "p.Caller", "kotlin.class", 3, type("Caller"))
    private val base = Graphs.symbol(target, "p.Base", "kotlin.class", 3, type("Base"))
    private val baseConstructor = Graphs.symbol(
        target,
        "p.Base.<init>",
        "kotlin.constructor",
        3,
        type("Base").method("<init>", "Int"),
    ).copy(container = base.id)

    private fun supertype(to: Symbol, line: Int) =
        Graphs.call(caller, to, file, line, RelationKind.EXTENDS).copy(subKind = "anonymous")

    private fun constructorCall(to: Symbol, line: Int) =
        Graphs.call(caller, to, file, line).copy(subKind = "constructor")

    private fun scored(edges: List<Relationship>, gold: GoldEdge) = run {
        val graph = FakeGraph(
            symbols = listOf(caller, base, baseConstructor),
            artifacts = listOf(Graphs.artifact(file), Graphs.artifact(target)),
            relationships = edges,
        )
        val goldFile = GoldClosedWorldFile("w", file, ":core", "main", "kotlin", "c", edges = listOf(gold))
        ClosedWorldScorer(GraphIndex(graph), GoldAddressResolver(graph)).score(goldFile)
    }

    private fun goldEdge(kind: String, line: Int = 12) = GoldEdge(
        line = line,
        column = 1,
        text = "Base",
        kind = kind,
        target = GoldAddress(fqName = "p.Base", file = target, line = 3),
    )

    @Test
    fun `the constructor call and the supertype of an anonymous object count as one match`() {
        val result = scored(listOf(supertype(base, 12), constructorCall(base, 12)), goldEdge("supertype"))

        assertEquals(1, result.score.matched)
        assertTrue(result.score.misses.isEmpty() && result.score.extras.isEmpty(), "${result.score}")
    }

    @Test
    fun `a call of the constructor of the supertype counts with the supertype of the same site`() {
        val edges = listOf(supertype(base, 12), constructorCall(baseConstructor, 12))

        val result = scored(edges, goldEdge("supertype"))

        assertEquals(1, result.score.matched)
        assertTrue(result.score.misses.isEmpty() && result.score.extras.isEmpty(), "${result.score}")
    }

    @Test
    fun `a constructor call on another line than the supertype stays an extra`() {
        val result = scored(listOf(supertype(base, 12), constructorCall(base, 13)), goldEdge("supertype"))

        assertEquals(1, result.score.matched)
        assertEquals(1, result.score.extras.size)
    }

    @Test
    fun `a constructor call without a supertype at its site is no pair`() {
        val result = scored(listOf(constructorCall(baseConstructor, 12)), goldEdge("constructor_call"))

        assertEquals(1, result.score.matched)
        assertTrue(result.score.extras.isEmpty())
    }
}
