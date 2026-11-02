package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpectActualConventionLinkerTest {
    private val draft = GraphDraft()
    private val common = draft.sourceSet(":lib", "commonMain")
    private val desktop = draft.sourceSet(":lib", "desktopMain", SetShape(dependsOn = listOf(common)))
    private val linker = ExpectActualConventionLinker()

    private fun edges() = linker.linkedFrom(draft)

    @Test
    fun `an actual in a source set that depends on the expect source set is bound by convention`() {
        val expect = draft.type(common, "p.Platform", setOf(SymbolFlag.EXPECT))
        val actual = draft.type(desktop, "p.Platform", setOf(SymbolFlag.ACTUAL))

        val edge = edges().single()

        assertEquals(actual.node, edge.from)
        assertEquals(RelationKind.ACTUALIZES, edge.kind)
        assertEquals(Resolution.Resolved(expect.node, Certainty.PROBABLE), edge.resolution)
        assertEquals(EvidenceClass.CONVENTION, edge.evidenceClass)
        assertEquals(actual.symbol.nameLocation, edge.site)
        assertEquals(actual.symbol.scope, edge.scope)
    }

    @Test
    fun `a pair that the compiler bound gets no second edge`() {
        val expect = draft.type(common, "p.Platform", setOf(SymbolFlag.EXPECT))
        val actual = draft.type(desktop, "p.Platform", setOf(SymbolFlag.ACTUAL))
        draft.relate(actual, RelationKind.ACTUALIZES, expect)

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `an actual in a source set that does not depend on the expect source set is left alone`() {
        val sibling = draft.sourceSet(":lib", "nativeMain")
        draft.type(common, "p.Platform", setOf(SymbolFlag.EXPECT))
        draft.type(sibling, "p.Platform", setOf(SymbolFlag.ACTUAL))

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `the dependency may go through other source sets`() {
        val shared = draft.sourceSet(":lib", "nativeMain", SetShape(dependsOn = listOf(common)))
        val arm = draft.sourceSet(":lib", "iosArm64Main", SetShape(dependsOn = listOf(shared)))
        val expect = draft.type(common, "p.Platform", setOf(SymbolFlag.EXPECT))
        val actual = draft.type(arm, "p.Platform", setOf(SymbolFlag.ACTUAL))

        val edge = edges().single()

        assertEquals(actual.node, edge.from)
        assertEquals(Resolution.Resolved(expect.node, Certainty.PROBABLE), edge.resolution)
    }

    @Test
    fun `declarations with another descriptor or in another module are not a pair`() {
        val other = draft.sourceSet(":other", "desktopMain")
        draft.type(common, "p.Platform", setOf(SymbolFlag.EXPECT))
        draft.type(desktop, "p.Other", setOf(SymbolFlag.ACTUAL))
        draft.type(other, "p.Platform", setOf(SymbolFlag.ACTUAL))

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `members are paired like classes and the edges come in a stable order`() {
        val android = draft.sourceSet(":lib", "androidMain", SetShape(dependsOn = listOf(common)))
        val expectClass = draft.type(common, "p.Platform", setOf(SymbolFlag.EXPECT))
        val expectMember = draft.function(common, "p.Platform.name", setOf(SymbolFlag.EXPECT), expectClass)
        draft.type(desktop, "p.Platform", setOf(SymbolFlag.ACTUAL))
        draft.function(desktop, "p.Platform.name", setOf(SymbolFlag.ACTUAL))
        draft.type(android, "p.Platform", setOf(SymbolFlag.ACTUAL))

        val edges = edges()

        assertEquals(3, edges.size)
        assertEquals(edges.map { it.from.toString() }.sorted(), edges.map { it.from.toString() })
        assertTrue(edges.any { (it.resolution as Resolution.Resolved).target == expectMember.node })
    }
}
