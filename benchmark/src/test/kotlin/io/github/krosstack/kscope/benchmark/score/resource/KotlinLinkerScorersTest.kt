package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.benchmark.score.at
import io.github.krosstack.kscope.benchmark.score.code.TestSubjectScorer
import io.github.krosstack.kscope.benchmark.score.edge
import io.github.krosstack.kscope.benchmark.score.extras
import io.github.krosstack.kscope.benchmark.score.goldOf
import io.github.krosstack.kscope.benchmark.score.misses
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

private const val STRINGS = "ui/src/commonMain/composeResources/values/strings.xml"
private const val SCREEN = "ui/src/commonMain/kotlin/p/Screen.kt"

class KotlinLinkerScorersTest {
    private fun resolver(graph: FakeGraph) = GoldAddressResolver(GraphIndex(graph))

    private fun index(graph: FakeGraph) = GraphIndex(graph)

    private val greeting =
        Graphs.symbol(STRINGS, "greeting", "compose.resource.string", 3, SymbolDescriptor().term("greeting"))
            .copy(kind = SymbolKind("compose.resource.string", SymbolCategory.RESOURCE))
    private val accessor = Graphs.symbol(SCREEN, "p.greeting", "kotlin.property", 1, Graphs.type("greetingAccessor"))
        .copy(
            origin = SymbolOrigin.Generated("compose.res", NodeRef.OfSymbol(greeting.id)),
            declaration = null,
            nameLocation = null,
        )
    private val screen = Graphs.symbol(SCREEN, "p.Screen", "kotlin.class", 5, Graphs.type("Screen"))

    private fun composeGraph(vararg extra: Relationship) = FakeGraph(
        symbols = listOf(greeting, accessor, screen),
        artifacts = listOf(Graphs.artifact(STRINGS, "compose.resource.values"), Graphs.artifact(SCREEN)),
        relationships = listOf(
            edge(greeting, RelationKind.GENERATES, accessor, EvidenceClass.CONVENTION),
            edge(screen, RelationKind.IMPORTS, accessor, EvidenceClass.COMPILER, at(SCREEN, 2)),
            edge(screen, RelationKind.REFERENCES, accessor, EvidenceClass.COMPILER, at(SCREEN, 7)),
        ) + extra,
    )

    private val composeGold = goldOf(
        "compose:1",
        "compose_resource_to_consumer",
        GoldAddress(file = STRINGS, kind = "compose.resource.string", name = "greeting", line = 3),
        GoldAddress(file = SCREEN, lines = listOf(7)),
        "generated-artifact",
    )

    @Test
    fun `a line that reads the accessor of a resource is its consumer and an import is not`() {
        val graph = composeGraph()

        val score = ComposeResourceScorer(index(graph), resolver(graph)).score(listOf(composeGold))

        assertEquals(emptyList(), score.misses())
        assertEquals(emptyList(), score.extras())
        assertEquals(0, score.evidenceMismatches)
        assertEquals(mapOf("convention" to 1), score.acceptedEvidence)
    }

    @Test
    fun `a line that gold does not list is an extra and a line that nothing reads is a miss`() {
        val another = edge(screen, RelationKind.CALLS, accessor, EvidenceClass.COMPILER, at(SCREEN, 9))
        val graph = composeGraph(another)

        val extra = ComposeResourceScorer(index(graph), resolver(graph)).score(listOf(composeGold))
        val missed = composeGold.copy(to = GoldAddress(file = SCREEN, lines = listOf(7, 11)))
        val missing = ComposeResourceScorer(index(graph), resolver(graph)).score(listOf(missed))

        assertEquals(1, extra.extras().size)
        assertEquals(1, missing.misses().size)
    }

    private fun testClass(name: String, path: String, line: Int) =
        Graphs.symbol(path, "p.$name", "kotlin.class", line, Graphs.type(name))

    private fun testGold(
        id: String,
        test: String,
        testPath: String,
        subject: String,
        subjectPath: String,
    ): GoldRelationship {
        val from = GoldAddress(fqName = "p.$test", file = testPath, kind = "test-class", line = 3)
        val to = GoldAddress(fqName = "p.$subject", file = subjectPath, kind = "subject", line = 3)
        return goldOf(id, "test_to_subject", from, to, "compiler")
    }

    @Test
    fun `a tests edge from the test class to its subject is scored with its evidence class`() {
        val greeterTest = testClass("GreeterTest", "t/GreeterTest.kt", 3)
        val greeter = testClass("Greeter", "m/Greeter.kt", 3)
        val formatterTest = testClass("FormatterTest", "t/FormatterTest.kt", 3)
        val formatter = testClass("Formatter", "m/Formatter.kt", 3)
        val graph = FakeGraph(
            symbols = listOf(greeterTest, greeter, formatterTest, formatter),
            artifacts = listOf("t/GreeterTest.kt", "m/Greeter.kt", "t/FormatterTest.kt", "m/Formatter.kt")
                .map { Graphs.artifact(it) },
            relationships = listOf(
                edge(greeterTest, RelationKind.TESTS, greeter, EvidenceClass.COMPILER),
                edge(formatterTest, RelationKind.TESTS, formatter, EvidenceClass.CONVENTION),
            ),
        )
        val gold = listOf(
            testGold("t:1", "GreeterTest", "t/GreeterTest.kt", "Greeter", "m/Greeter.kt"),
            testGold("t:2", "FormatterTest", "t/FormatterTest.kt", "Formatter", "m/Formatter.kt"),
        )

        val score = TestSubjectScorer(index(graph), resolver(graph)).score(gold)

        assertEquals(emptyList(), score.misses())
        assertEquals(emptyList(), score.extras())
        assertEquals(1, score.evidenceMismatches, "Gold expects the compiler; one edge states a convention")
    }

    @Test
    fun `a test bound to another subject than gold names is a miss and an extra`() {
        val greeterTest = testClass("GreeterTest", "t/GreeterTest.kt", 3)
        val greeter = testClass("Greeter", "m/Greeter.kt", 3)
        val other = testClass("Other", "m/Other.kt", 3)
        val graph = FakeGraph(
            symbols = listOf(greeterTest, greeter, other),
            artifacts = listOf("t/GreeterTest.kt", "m/Greeter.kt", "m/Other.kt").map { Graphs.artifact(it) },
            relationships = listOf(edge(greeterTest, RelationKind.TESTS, other, EvidenceClass.COMPILER)),
        )

        val score = TestSubjectScorer(index(graph), resolver(graph))
            .score(listOf(testGold("t:1", "GreeterTest", "t/GreeterTest.kt", "Greeter", "m/Greeter.kt")))

        assertEquals(1, score.misses().size)
        assertEquals(1, score.extras().size)
    }

    @Test
    fun `a gold relationship that cannot be mapped at either end is one miss and not two`() {
        val greeterTest = testClass("GreeterTest", "t/GreeterTest.kt", 3)
        val greeter = testClass("Greeter", "m/Greeter.kt", 3)
        val graph = FakeGraph(
            symbols = listOf(greeterTest, greeter),
            artifacts = listOf("t/GreeterTest.kt", "m/Greeter.kt").map { Graphs.artifact(it) },
            relationships = listOf(edge(greeterTest, RelationKind.TESTS, greeter, EvidenceClass.COMPILER)),
        )
        val gold = listOf(
            testGold("t:1", "GreeterTest", "t/GreeterTest.kt", "Greeter", "m/Greeter.kt"),
            testGold("t:2", "GhostTest", "t/GhostTest.kt", "Phantom", "m/Phantom.kt"),
        )

        val score = TestSubjectScorer(index(graph), resolver(graph)).score(gold)

        assertEquals(2, score.unmapped.size)
        assertEquals(1, score.misses().size)
        assertEquals(0.5, score.score?.recall)
    }
}
