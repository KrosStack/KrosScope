package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariantAlternativeLinkerTest {
    private val draft = GraphDraft()
    private val main = draft.sourceSet(":app", "main", SetShape(variants = listOf("demoDebug", "fullDebug")))
    private val demo = draft.sourceSet(":app", "demo", SetShape(variants = listOf("demoDebug")))
    private val full = draft.sourceSet(":app", "full", SetShape(variants = listOf("fullDebug")))
    private val linker = VariantAlternativeLinker()

    private fun edges() = linker.linkedFrom(draft)

    @Test
    fun `the same declaration in two flavors is bound once, from the source set that sorts first`() {
        val inDemo = draft.type(demo, "p.FlavorFlags")
        val inFull = draft.type(full, "p.FlavorFlags")

        val edge = edges().single()

        assertEquals(inDemo.node, edge.from)
        assertEquals(RelationKind.BINDS, edge.kind)
        assertEquals("variant-alternative", edge.subKind)
        assertEquals(Resolution.Resolved(inFull.node, Certainty.EXACT), edge.resolution)
        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertNull(edge.site, "The alternatives are expressed nowhere in the code")
    }

    @Test
    fun `the edge is heuristic when a source set of the pair is not exact in the build model`() {
        val paidShape = SetShape(variants = listOf("paidDebug"), certainty = Certainty.HEURISTIC)
        val paid = draft.sourceSet(":app", "paid", paidShape)
        val inDemo = draft.type(demo, "p.FlavorFlags")
        val inFull = draft.type(full, "p.FlavorFlags")
        val inPaid = draft.type(paid, "p.FlavorFlags")

        val certaintyByPair = edges().associate { edge ->
            val resolved = edge.resolution as Resolution.Resolved
            (edge.from to resolved.target) to resolved.certainty
        }

        assertEquals(
            mapOf(
                (inDemo.node to inFull.node) to Certainty.EXACT,
                (inDemo.node to inPaid.node) to Certainty.HEURISTIC,
                (inFull.node to inPaid.node) to Certainty.HEURISTIC,
            ),
            certaintyByPair,
        )
        assertEquals(setOf(EvidenceClass.BUILD_MODEL), edges().map { it.evidenceClass }.toSet())
    }

    @Test
    fun `three flavors give one edge for each pair`() {
        val paid = draft.sourceSet(":app", "paid", SetShape(variants = listOf("paidDebug")))
        draft.type(demo, "p.FlavorFlags")
        draft.type(full, "p.FlavorFlags")
        draft.type(paid, "p.FlavorFlags")

        assertEquals(3, edges().size)
    }

    @Test
    fun `a declaration of the main source set is no alternative of a flavor`() {
        draft.type(main, "p.FlavorFlags")
        draft.type(demo, "p.FlavorFlags")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `source sets that share a variant are no alternatives`() {
        val debug = draft.sourceSet(":app", "debug", SetShape(variants = listOf("demoDebug", "fullDebug")))
        draft.type(demo, "p.Tracing")
        draft.type(debug, "p.Tracing")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `source sets related by dependsOn are no alternatives`() {
        val childShape = SetShape(dependsOn = listOf(demo), variants = listOf("childDebug"))
        val child = draft.sourceSet(":app", "demoChild", childShape)
        draft.type(demo, "p.Shared")
        draft.type(child, "p.Shared")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `declarations of other descriptors or of other modules are no alternatives`() {
        val otherDemo = draft.sourceSet(":lib", "demo", SetShape(variants = listOf("libDemo")))
        draft.type(demo, "p.FlavorFlags")
        draft.type(full, "p.Other")
        draft.type(otherDemo, "p.FlavorFlags")

        assertEquals(emptyList(), edges())
    }

    @Test
    fun `members are bound like their classes`() {
        val flagsInDemo = draft.type(demo, "p.FlavorFlags")
        val flagsInFull = draft.type(full, "p.FlavorFlags")
        val memberInDemo = draft.function(demo, "p.FlavorFlags.enabled", container = flagsInDemo)
        val memberInFull = draft.function(full, "p.FlavorFlags.enabled", container = flagsInFull)

        val targets = edges().associate { it.from to (it.resolution as Resolution.Resolved).target }

        assertEquals(memberInFull.node, targets[memberInDemo.node])
        assertEquals(flagsInFull.node, targets[flagsInDemo.node])
    }
}
