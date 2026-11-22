package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.diagram.build.DiagramCut
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.model.RelationKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val callKinds = setOf(RelationKind.CALLS)

private val typeKinds = setOf(
    RelationKind.CALLS,
    RelationKind.REFERENCES,
    RelationKind.TYPE_USES,
    RelationKind.EXTENDS,
    RelationKind.IMPLEMENTS,
)

private data class Run(val case: Case, val budget: Int, val depth: Int, val links: Int)

private data class Case(val name: String, val world: SymbolWorld, val node: String, val kinds: Set<RelationKind>)

private fun mixedWorld(): SymbolWorld {
    val world = SymbolWorld()
    val hub = world.type("Hub")
    val member = world.function("m", hub)
    val twin = world.function("n", hub)
    world.call(member, twin, 1)
    world.call(world.function("top"), member, 2)
    world.call(world.facadeFunction("helper", "UtilKt"), member, 3)
    world.call(member, world.function("other"), 4)
    world.callOutside(member)
    world.callMissing(twin, 5)
    world.unresolvedCall(twin, 6)
    world.call(world.property("holder", world.type("Holder")), member, 7)
    world.call(member, world.type("Widget"), 8)
    val farRun = world.function("run", world.type("Far"))
    world.call(farRun, member, 9)
    world.call(world.function("run", world.type("Farther")), farRun, 10)
    return world
}

internal class ReadSumTest {
    private val cases = listOf(
        Case("call flow", callFlowWorld(), "app.Checkout.pay", callKinds),
        Case("call flow of a middle callable", callFlowWorld(), "app.Checkout.validate", callKinds),
        Case("types", typeFlowWorld(), "app.Checkout", typeKinds),
        Case("hub", hubWorld(30), "app.Hub", typeKinds),
        Case("wide", wideWorld(30), "app.Wide", typeKinds),
        Case("construction calls", constructionWorld(), "app.Factory.build", callKinds),
        Case("construction types", constructionWorld(), "app.Factory", typeKinds),
        Case("chain", chainWorld(6), "app.f0002", callKinds),
        Case("mixed types", mixedWorld(), "app.Hub", typeKinds),
        Case("mixed calls", mixedWorld(), "app.Hub.m", callKinds),
        Case("mixed calls of the second", mixedWorld(), "app.Hub.n", callKinds),
    )

    private fun runs(): List<Run> = cases.flatMap { case ->
        listOf(3, 6, 12, 25, 60, 1_200).flatMap { budget ->
            (0..2).flatMap { depth -> listOf(1, 3, 300).map { links -> Run(case, budget, depth, links) } }
        }
    }

    private fun build(graph: SymbolGraph, case: Case, budget: Int, depth: Int, links: Int): Diagram {
        val request = DiagramRequest(DiagramKind.SYMBOL, case.node, depth, links, Int.MAX_VALUE)
        val result = SymbolDiagramBuilder(graph, budget).build(request) {
            DiagramCut().apply(it, request) { diagram -> MermaidEmitter.render(diagram).length }
        }
        return assertIs<DiagramResult.Built>(result).diagram
    }

    private fun Diagram.accounted(): Int {
        val reasons = with(omitted) {
            notInType + notCallable + withoutSymbol + withinType + beyondDepth + stopped
        }
        return edges.sumOf { it.byState.values.sum() } + omitted.linksByState.values.sum() + reasons
    }

    @Test
    fun `the relationships of the worlds are all different`() {
        cases.forEach { assertEquals(it.world.relationships.size, it.world.relationships.distinct().size, it.name) }
    }

    @Test
    fun `the drawn links the links cut by the budget and the reasons add up to the relationships that were read`() {
        for (run in runs()) {
            val graph = SymbolGraph(run.case.world)
            val diagram = build(graph, run.case, run.budget, run.depth, run.links)
            val read = graph.served.count { it.kind in run.case.kinds }
            assertEquals(read, diagram.accounted(), "${run.case.name}: $run")
        }
    }

    @Test
    fun `the sum holds whatever order the reader returns`() {
        for (case in cases) {
            for (mix in 1..2) {
                val graph = SymbolGraph(case.world, mix)
                val diagram = build(graph, case, 1_200, 2, 300)
                assertEquals(graph.served.count { it.kind in case.kinds }, diagram.accounted(), "${case.name} $mix")
            }
        }
    }
}
