package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.ClassPart
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun resource(path: String, type: String, name: String, line: Int): Symbol =
    Graphs.symbol(path, name, "android.resource.$type", line, SymbolDescriptor().term(type).term(name))
        .copy(kind = SymbolKind("android.resource.$type", SymbolCategory.RESOURCE))

private fun relationship(from: Symbol, to: Symbol, path: String, line: Int) = Relationship(
    from = NodeRef.OfSymbol(from.id),
    kind = RelationKind.REFERENCES,
    subKind = null,
    resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
    evidenceClass = EvidenceClass.TEXTUAL,
    site = SourceLocation(Graphs.ref(path), TextRange(line, 3, line, 20)),
    scope = Scope(),
)

private fun compiler(edge: Relationship) = edge.copy(evidenceClass = EvidenceClass.COMPILER)

private fun generates(from: Symbol, to: Symbol) = relationship(from, to, "", 1)
    .copy(kind = RelationKind.GENERATES, evidenceClass = EvidenceClass.CONVENTION, site = null)

private fun declaration(type: String, name: String, file: String, line: Int) =
    GoldAddress(module = ":app", file = file, kind = "android.resource.$type", name = name, line = line)

private fun gold(id: String, from: GoldAddress, consumer: GoldAddress, evidence: String) =
    GoldRelationship(id, "resource_to_consumer", from, consumer, evidence)

private fun consumer(file: String, kind: String, line: Int) = GoldAddress(
    file = file,
    kind = kind,
    lines = listOf(line),
)

class ResourceToConsumerScorerTest {
    private val strings = "app/src/main/res/values/strings.xml"
    private val layout = "app/src/main/res/layout/main.xml"
    private val manifest = "app/src/main/AndroidManifest.xml"
    private val code = "app/src/main/kotlin/Main.kt"

    private val appName = resource(strings, "string", "app_name", 3)
    private val title = resource(strings, "string", "title", 4)
    private val mainLayout = resource(layout, "layout", "main", 1)
    private val titleId = resource(layout, "id", "title_view", 6)

    private val field = Graphs.symbol("", "p.R.string.app_name", "java.field", 1, SymbolDescriptor().term("R.app_name"))
        .copy(declaration = null, nameLocation = null, origin = SymbolOrigin.Generated("android.r", null))
    private val holder = Graphs.symbol("", "p.R.string", "java.class", 1, SymbolDescriptor().term("R.string"))
        .copy(declaration = null, nameLocation = null, origin = SymbolOrigin.Generated("android.r", null))
    private val reader = Graphs.symbol(code, "p.Main", "kotlin.class", 5, Graphs.type("Main"))

    private val graph = FakeGraph(
        symbols = listOf(appName, title, mainLayout, titleId, field, holder, reader),
        artifacts = listOf(
            Graphs.artifact(strings, "android.resource.values"),
            Graphs.artifact(layout, "android.resource.layout"),
            Graphs.artifact(manifest, "android.manifest"),
            Graphs.artifact(code),
        ),
        relationships = listOf(
            relationship(mainLayout, appName, layout, 9),
            relationship(mainLayout, titleId, layout, 12),
            relationship(appName, appName, strings, 3).copy(subKind = "definition"),
            generates(appName, field),
            compiler(relationship(reader, field, code, 7)),
            compiler(relationship(reader, holder, code, 7)),
        ),
    )

    private val appNameAddress = declaration("string", "app_name", strings, 3)

    private val goldRelationships = listOf(
        gold("r:1", appNameAddress, consumer(layout, "consumer.xml.layout", 9), "textual"),
        gold("r:2", appNameAddress, consumer(code, "consumer.kotlin", 7), "generated-artifact"),
        gold("r:3", appNameAddress, consumer(manifest, "consumer.xml.manifest", 10), "textual"),
        gold(
            "r:4",
            declaration("layout", "main", layout, 1),
            consumer(code, "consumer.kotlin", 8),
            "generated-artifact",
        ),
    )

    private val score = ResourceToConsumerScorer(GraphIndex(graph), GoldAddressResolver(graph)).score(goldRelationships)

    private fun part(label: String): ClassPart = score.parts.single { it.label == label }

    @Test
    fun `xml consumers are the resolved references between resources and nothing else`() {
        val xml = part(ConsumerParts.XML)
        assertEquals(1, xml.score.expected)
        assertEquals(1, xml.score.matched)
        assertEquals(emptyList(), xml.score.extras, "A definition site and an id are not consumers gold lists")
        assertEquals(0, xml.evidenceMismatches)
    }

    @Test
    fun `a code consumer is reached through the generated field and the holder class is not a consumer`() {
        val code = part(ConsumerParts.CODE)
        assertEquals(1, code.score.matched)
        assertEquals(emptyList(), code.score.extras)
        assertEquals(0, code.evidenceMismatches, "A convention hop to the field stands for generator output")
        assertEquals(mapOf("convention" to 1), code.acceptedEvidence)
    }

    @Test
    fun `consumers without a producer stay in the score as misses and are told apart`() {
        assertEquals(1, part(ConsumerParts.MANIFEST).score.misses.size)
        assertEquals(0.0, part(ConsumerParts.MANIFEST).score.recall)
        assertEquals(1, part(ConsumerParts.CODE_LAYOUT).score.misses.size)
        assertEquals(4, score.goldCount)
        assertEquals(2, score.score?.matched)
        assertEquals(2, score.score?.misses?.size)
        assertEquals(1.0, score.score?.precision)
    }

    @Test
    fun `a declaration that the graph does not have is a miss with its reason`() {
        val ghost = declaration("string", "ghost", strings, 99)
        val unmapped = ResourceToConsumerScorer(GraphIndex(graph), GoldAddressResolver(graph))
            .score(
                listOf(
                    gold(
                        "r:9",
                        ghost,
                        GoldAddress(file = layout, kind = "consumer.xml.layout", lines = listOf(9)),
                        "textual",
                    ),
                ),
            )
        assertEquals(1, unmapped.unmapped.size)
        assertTrue(unmapped.unmapped.single().startsWith("r:9: no-matching-symbol"))
        assertEquals(0, unmapped.score?.matched)
    }
}
