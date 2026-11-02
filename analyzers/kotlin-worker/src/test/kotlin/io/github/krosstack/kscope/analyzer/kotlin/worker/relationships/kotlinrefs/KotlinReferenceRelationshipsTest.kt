package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinReferenceRelationshipsTest {
    private val edges = RelationFacts.langEdges
    private val located = RelationFacts.langLocatedEdges
    private val run = "rel/lang/User#run(Point,Box)."

    @Test
    fun `every relationship is compiler evidence of the analysed source set`() {
        val all = RelationFacts.lang.relationships()
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.evidenceClass == EvidenceClass.COMPILER })
        assertTrue(all.all { it.scope.sourceSet?.value == "sample//:lang@main" && it.site != null })
    }

    @Test
    fun `resolved references to workspace declarations are exact`() {
        val resolved = RelationFacts.lang.relationships().map { it.resolution }.filterIsInstance<Resolution.Resolved>()
        assertTrue(resolved.isNotEmpty() && resolved.all { it.certainty == Certainty.EXACT })
    }

    @Test
    fun `calls pick the overload the compiler selects`() {
        Edges.assertHas(edges, "$run calls -> rel/lang/over(Int).", "$run calls -> rel/lang/over(String).")
    }

    @Test
    fun `extension functions extension properties and infix calls resolve to their declarations`() {
        Edges.assertHas(
            edges,
            "$run calls -> rel/lang/shout(this:String).",
            "$run references/read -> rel/lang/shoutLength(this:String).",
            "$run calls -> rel/lang/pair(this:Int,Int).",
        )
    }

    @Test
    fun `property accesses are told apart as reads and writes`() {
        Edges.assertHas(
            located,
            "$run references/write -> rel/lang/Point#y. @19:15",
            "$run references/read -> rel/lang/Point#x. @20:26",
            "$run references/read -> rel/lang/Point#y. @20:36",
            "$run references/write -> rel/lang/Registry#count. @16:18",
            "$run references/read -> rel/lang/Registry#LIMIT. @15:30",
        )
    }

    @Test
    fun `increments and compound assignments write the property`() {
        Edges.assertHas(
            located,
            "$run references/write -> rel/lang/User#counter. @17:9",
            "$run references/write -> rel/lang/User#counter. @18:9",
            "rel/lang/Registry#register(). references/write -> rel/lang/Registry#count. @39:29",
        )
    }

    @Test
    fun `callable references and class literals are references`() {
        Edges.assertHas(
            edges,
            "$run references/callable-reference -> rel/lang/helper().",
            "$run references/callable-reference -> rel/lang/Child#hello().",
            "$run references -> rel/lang/Child#",
        )
    }

    @Test
    fun `an annotation is a reference from the annotated symbol and its class literal arguments are too`() {
        Edges.assertHas(
            edges,
            "rel/lang/User# references/annotation -> rel/lang/Marker#",
            "rel/lang/User# references -> rel/lang/Child#",
            "rel/lang/User# references/named-argument -> rel/lang/Marker#level.",
        )
    }

    @Test
    fun `type arguments type aliases and signature types are type uses`() {
        Edges.assertHas(
            edges,
            "rel/lang/User#names. type-uses -> rel/lang/Names#",
            "$run type-uses -> rel/lang/Box#",
            "$run type-uses -> rel/lang/Point#",
            "rel/lang/Box#plus(Box). type-uses -> rel/lang/Box#",
        )
    }

    @Test
    fun `references to library declarations are dropped`() {
        val targets = RelationFacts.lang.relationships().mapNotNull { (it.resolution as? Resolution.Resolved)?.target }
        val libraryTargets = targets.mapNotNull { (it as? NodeRef.OfSymbol)?.id }
            .filter { !it.value.startsWith("kscope gradle ") }
        assertEquals(emptyList(), libraryTargets.map { it.value })
    }
}
