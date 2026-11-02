package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.retrieval.relationship
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TestSelectionTest {
    private val fixture = TestSelectionFixture()

    @Test
    fun `test subject edges retain connected tests and exclude same module only associations`() {
        val connected = relationship(fixture.node(fixture.connected), fixture.node(fixture.subject), RelationKind.TESTS)
        val unrelated = connected.copy(
            from = fixture.node(fixture.unrelated),
            attributes = mapOf("tests.subject-basis" to "module"),
        )
        val selected = TestSelection(fixture.graph(listOf(connected, unrelated)))
            .select(listOf(fixture.edit()))

        assertEquals(listOf(fixture.connected.ref), selected.map { it.artifact })
        assertEquals(SignalKind.TEST_RELATION, selected.single().reasons.single().signal)
    }

    @Test
    fun `direct references from test declarations select their test file`() {
        for (kind in listOf(RelationKind.CALLS, RelationKind.REFERENCES, RelationKind.TYPE_USES)) {
            val edge = relationship(fixture.node(fixture.connected), fixture.node(fixture.subject), kind)
            val selected = TestSelection(fixture.graph(listOf(edge))).select(listOf(fixture.edit()))

            assertEquals(listOf(fixture.connected.ref), selected.map { it.artifact })
            assertEquals(SignalKind.TEST_REFERENCE, selected.single().reasons.single().signal)
        }
    }

    @Test
    fun `unresolved associations never select a test`() {
        val edge = relationship(fixture.node(fixture.connected), fixture.node(fixture.subject), RelationKind.TESTS)
            .copy(resolution = Resolution.Unresolved("subject", ReasonCode("test.missing")))

        assertTrue(TestSelection(fixture.graph(listOf(edge))).select(listOf(fixture.edit())).isEmpty())
    }

    @Test
    fun `test only edits retain the edited test without a subject edge`() {
        val selected = TestSelection(fixture.graph(emptyList()))
            .select(listOf(fixture.edit(fixture.connected)))

        assertEquals(listOf(fixture.connected.ref), selected.map { it.artifact })
        assertEquals(SignalKind.TEST_EDIT, selected.single().reasons.single().signal)
    }

    @Test
    fun `selection is deterministic and removes weaker references beyond a clear subject gap`() {
        val subject = relationship(fixture.node(fixture.connected), fixture.node(fixture.subject), RelationKind.TESTS)
        val reference = relationship(fixture.node(fixture.unrelated), fixture.node(fixture.subject), RelationKind.CALLS)
        val edges = listOf(reference, subject)
        val selected = TestSelection(fixture.graph(edges)).select(listOf(fixture.edit()))

        assertEquals(listOf(fixture.connected.ref), selected.map { it.artifact })
        assertEquals(
            selected,
            TestSelection(fixture.graph(edges, reversed = true)).select(listOf(fixture.edit())),
        )
    }

    @Test
    fun `subject containers connect a ranked member to its class test`() {
        val parent = fixture.declarations.first()
        val member = parent.copy(id = SymbolId("member"), name = "member", container = parent.id)
        val edge = relationship(fixture.node(fixture.connected), fixture.node(fixture.subject), RelationKind.TESTS)
        val graph = fixture.graph(listOf(edge), extraSymbols = listOf(member))

        assertEquals(
            listOf(fixture.connected.ref),
            TestSelection(graph).select(listOf(fixture.edit())).map { it.artifact },
        )
    }

    @Test
    fun `test fixtures are excluded while instrumented tests are selected`() {
        val edge = relationship(fixture.node(fixture.connected), fixture.node(fixture.subject), RelationKind.TESTS)
        val fixtures = fixture.graph(listOf(edge), testRole = SourceSetRole.TEST_FIXTURES)
        val instrumented = fixture.graph(listOf(edge), testRole = SourceSetRole.INSTRUMENTED_TEST)

        assertTrue(TestSelection(fixtures).select(listOf(fixture.edit())).isEmpty())
        assertEquals(
            listOf(fixture.connected.ref),
            TestSelection(instrumented).select(listOf(fixture.edit())).map { it.artifact },
        )
    }

    @Test
    fun `equal strength subjects use stable path ordering without dropping ties`() {
        val edges = listOf(fixture.unrelated, fixture.connected).map {
            relationship(fixture.node(it), fixture.node(fixture.subject), RelationKind.TESTS)
        }
        val selected = TestSelection(fixture.graph(edges)).select(listOf(fixture.edit()))

        assertEquals(listOf(fixture.connected.ref, fixture.unrelated.ref), selected.map { it.artifact })
        assertEquals(
            selected,
            TestSelection(fixture.graph(edges, reversed = true)).select(listOf(fixture.edit())),
        )
    }

    @Test
    fun `generated origin connects a test reference to the edited source of truth`() {
        val generated = fixture.declarations.first().copy(
            id = SymbolId("generated"),
            declaration = null,
            origin = SymbolOrigin.Generated("schema", NodeRef.OfArtifact(fixture.subject.ref)),
        )
        val edge = relationship(fixture.node(fixture.connected), NodeRef.OfSymbol(generated.id))
        val graph = fixture.graph(listOf(edge), extraSymbols = listOf(generated))

        assertEquals(
            listOf(fixture.connected.ref),
            TestSelection(graph).select(listOf(fixture.edit())).map { it.artifact },
        )
    }

    @Test
    fun `generates edge connects a test reference to the edited source declaration`() {
        val generated = fixture.declarations.first().copy(id = SymbolId("generated"), declaration = null)
        val edge = relationship(fixture.node(fixture.connected), NodeRef.OfSymbol(generated.id))
        val generates = relationship(
            fixture.node(fixture.subject),
            NodeRef.OfSymbol(generated.id),
            RelationKind.GENERATES,
        )
        val graph = fixture.graph(listOf(edge, generates), extraSymbols = listOf(generated))

        assertEquals(
            listOf(fixture.connected.ref),
            TestSelection(graph).select(listOf(fixture.edit())).map { it.artifact },
        )
    }

    @Test
    fun `unranked declarations in an edited file remain test subjects`() {
        val parent = fixture.declarations.first()
        val ranked = parent.copy(id = SymbolId("ranked"), container = parent.id)
        val unranked = parent.copy(id = SymbolId("unranked"), container = parent.id)
        val edge = relationship(fixture.node(fixture.connected), NodeRef.OfSymbol(unranked.id))
        val graph = fixture.graph(listOf(edge), extraSymbols = listOf(ranked, unranked))

        assertEquals(
            listOf(fixture.connected.ref),
            TestSelection(graph).select(listOf(fixture.edit()))
                .map { it.artifact },
        )
    }
}
