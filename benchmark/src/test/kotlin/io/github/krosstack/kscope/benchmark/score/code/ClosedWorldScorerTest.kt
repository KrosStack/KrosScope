package io.github.krosstack.kscope.benchmark.score.code

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldClosedWorldFile
import io.github.krosstack.kscope.benchmark.gold.GoldEdge
import io.github.krosstack.kscope.benchmark.gold.GoldImport
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.benchmark.score.edge
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SymbolDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClosedWorldScorerTest {
    private val file = "core/src/main/kotlin/p/Caller.kt"
    private val target = "core/src/main/kotlin/p/Target.kt"

    private fun type(name: String) = SymbolDescriptor().namespace("p").type(name)

    private val caller = Graphs.symbol(file, "p.Caller", "kotlin.class", 3, type("Caller"))
    private val callee = Graphs.symbol(target, "p.Callee", "kotlin.class", 3, type("Callee"))
    private val calleeConstructor = Graphs.symbol(
        target,
        "p.Callee.<init>",
        "kotlin.constructor",
        3,
        type("Callee").method("<init>", "Int"),
    ).copy(container = callee.id)
    private val helper = Graphs.symbol(target, "p.helper", "kotlin.function", 9, SymbolDescriptor().method("helper"))

    private fun scorer(vararg edges: Relationship): ClosedWorldScorer {
        val graph = FakeGraph(
            symbols = listOf(caller, callee, calleeConstructor, helper),
            artifacts = listOf(Graphs.artifact(file), Graphs.artifact(target)),
            relationships = edges.toList(),
        )
        return ClosedWorldScorer(GraphIndex(graph), GoldAddressResolver(graph))
    }

    private fun gold(vararg edges: GoldEdge, imports: List<GoldImport> = emptyList()) =
        GoldClosedWorldFile("w", file, ":core", "main", "kotlin", "c", imports = imports, edges = edges.toList())

    private fun edge(line: Int, kind: String, fqName: String, targetLine: Int, addressKind: String? = null) = GoldEdge(
        line = line,
        column = 1,
        text = "x",
        kind = kind,
        target = GoldAddress(fqName = fqName, file = target, line = targetLine, kind = addressKind),
    )

    @Test
    fun `an edge matches on the source line and the target`() {
        val result = scorer(Graphs.call(caller, helper, file, 12)).score(gold(edge(12, "call", "p.helper", 9)))
        assertEquals(1.0, result.score.precision)
        assertEquals(1.0, result.score.recall)
    }

    @Test
    fun `an edge on another line is a miss and an extra`() {
        val result = scorer(Graphs.call(caller, helper, file, 13)).score(gold(edge(12, "call", "p.helper", 9)))
        assertEquals(1, result.score.misses.size)
        assertEquals(1, result.score.extras.size)
        assertEquals(listOf("call"), result.byKind.filter { it.matched == 0 }.map { it.kind })
    }

    @Test
    fun `a constructor call addressed by the class matches the primary constructor`() {
        val result = scorer(Graphs.call(caller, calleeConstructor, file, 12))
            .score(gold(edge(12, "constructor_call", "p.Callee", 3)))
        assertTrue(result.score.misses.isEmpty() && result.score.extras.isEmpty(), "${result.score}")
    }

    @Test
    fun `edges to generated surfaces are deferred and not scored`() {
        val deferred = edge(12, "call", "p.Queries", 1, addressKind = "generated.sqldelight-query")
        val result = scorer().score(gold(deferred))
        assertEquals(1, result.deferred.size)
        assertEquals(0, result.score.expected)
    }

    @Test
    fun `an edge whose target cannot be mapped is a miss that names the reason`() {
        val result = scorer().score(gold(edge(12, "call", "p.gone", 9)))
        assertEquals(1, result.score.misses.size)
        assertEquals(1, result.unmapped.size)
        assertTrue("no-matching-symbol" in result.unmapped.single())
    }

    @Test
    fun `imports are compared with the import edges of the file`() {
        val import = GoldImport(2, GoldAddress(fqName = "p.Callee", file = target, line = 3), "p.Callee")
        val relationship = Graphs.call(caller, callee, file, 2, RelationKind.IMPORTS)
        val result = scorer(relationship).score(gold(imports = listOf(import)))
        assertEquals(1, result.score.matched)
        assertTrue(result.score.extras.isEmpty())
    }
}
