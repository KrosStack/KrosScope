package io.github.krosstack.kscope.benchmark.score.code

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.benchmark.score.at
import io.github.krosstack.kscope.benchmark.score.edge
import io.github.krosstack.kscope.benchmark.score.extras
import io.github.krosstack.kscope.benchmark.score.goldOf
import io.github.krosstack.kscope.benchmark.score.misses
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

private const val FLAGS_DEMO = "app/src/demo/kotlin/p/FlavorFlags.kt"
private const val FLAGS_FULL = "app/src/full/kotlin/p/FlavorFlags.kt"

class FlavorImplementationScorerTest {
    private fun resolver(graph: FakeGraph) = GoldAddressResolver(GraphIndex(graph))

    private fun index(graph: FakeGraph) = GraphIndex(graph)

    private fun flavor(file: String, sourceSet: String, line: Int): Symbol {
        val descriptor = Graphs.type("FlavorFlags")
        return Graphs.symbol(file, "p.FlavorFlags", "kotlin.object", line, descriptor).copy(
            id = SymbolIdentity.workspace(Graphs.moduleId, sourceSet, descriptor).toSymbolId(),
            scope = Scope(),
        )
    }

    private val flagsFile = "app/src/main/kotlin/p/FeatureFlags.kt"
    private val appFile = "app/src/main/kotlin/p/App.kt"
    private val interfaceFlags =
        Graphs.symbol(flagsFile, "p.FeatureFlags", "kotlin.interface", 3, Graphs.type("FeatureFlags"))
    private val demo = flavor(FLAGS_DEMO, "demo", 6)
    private val full = flavor(FLAGS_FULL, "full", 6)
    private val app = Graphs.symbol(appFile, "p.App.flags", "kotlin.property", 6, Graphs.type("App").term("flags"))

    private fun flavorGraph(withAlternative: Boolean): FakeGraph {
        val alternative = edge(demo, RelationKind.BINDS, full, EvidenceClass.BUILD_MODEL)
            .copy(subKind = "variant-alternative")
        return FakeGraph(
            symbols = listOf(interfaceFlags, demo, full, app),
            artifacts = listOf(FLAGS_DEMO, FLAGS_FULL, flagsFile, appFile).map { Graphs.artifact(it) },
            relationships = listOfNotNull(
                edge(demo, RelationKind.IMPLEMENTS, interfaceFlags, EvidenceClass.COMPILER),
                edge(app, RelationKind.REFERENCES, demo, EvidenceClass.COMPILER, at(appFile, 6)),
                alternative.takeIf { withAlternative },
            ),
        )
    }

    private fun flavorGold(compilerBoundEvidence: String = "compiler"): List<GoldRelationship> {
        fun target(file: String) = GoldAddress(fqName = "p.FlavorFlags", file = file, kind = "object", line = 6)
        val shared = GoldAddress(fqName = "p.FeatureFlags", file = flagsFile, line = 3)
        val usage = GoldAddress(fqName = "p.App.flags", file = appFile, line = 6)
        return listOf(
            goldOf("f:1", "flavor_specific_implementation", shared, target(FLAGS_DEMO), compilerBoundEvidence),
            goldOf("f:2", "flavor_specific_implementation", usage, target(FLAGS_DEMO), compilerBoundEvidence),
            goldOf("f:3", "flavor_specific_implementation", shared, target(FLAGS_FULL), "build-model"),
            goldOf("f:4", "flavor_specific_implementation", usage, target(FLAGS_FULL), "build-model"),
        )
    }

    @Test
    fun `the interface and the usage site reach the implementation of every flavor through the alternative`() {
        val graph = flavorGraph(withAlternative = true)

        val score = FlavorImplementationScorer(index(graph), resolver(graph)).score(flavorGold())

        assertEquals(emptyList(), score.misses())
        assertEquals(emptyList(), score.extras())
        assertEquals(0, score.evidenceMismatches)
    }

    @Test
    fun `the implementation that the compiler reaches carries the compiler as its evidence`() {
        val graph = flavorGraph(withAlternative = true)

        val score = FlavorImplementationScorer(index(graph), resolver(graph)).score(flavorGold("build-model"))

        assertEquals(2, score.evidenceMismatches, "Nothing is relaxed: the compiler bound the implementation of demo")
    }

    @Test
    fun `without the alternative no pair is flavor-specific`() {
        val graph = flavorGraph(withAlternative = false)

        val score = FlavorImplementationScorer(index(graph), resolver(graph)).score(flavorGold())

        assertEquals(4, score.misses().size)
        assertEquals(emptyList(), score.extras())
    }
}
