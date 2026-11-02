package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinDeclarationRelationshipsTest {
    private val edges = RelationFacts.langEdges
    private val run = "rel/lang/User#run(Point,Box)."

    @Test
    fun `an anonymous object implements its interface from the enclosing symbol`() {
        Edges.assertHas(edges, "$run implements/anonymous -> rel/lang/Shape#")
        assertTrue(edges.none { it.contains("anonymous") && !it.startsWith(run) })
    }

    @Test
    fun `an anonymous object that extends a class calls its constructor`() {
        Edges.assertHas(
            edges,
            "$run extends/anonymous -> rel/lang/Base#",
            "$run calls -> rel/lang/Base#`<init>`(Int).",
        )
    }

    @Test
    fun `declared supertypes and overrides are inheritance relationships`() {
        Edges.assertHas(
            edges,
            "rel/lang/Child# extends -> rel/lang/Base#",
            "rel/lang/Child# implements -> rel/lang/Shape#",
            "rel/lang/Child#scale(Int). overrides -> rel/lang/Shape#scale(Int).",
            "rel/lang/Child#area. overrides -> rel/lang/Shape#area.",
            "rel/lang/Child#hello(). overrides -> rel/lang/Base#hello().",
        )
    }

    @Test
    fun `constructor calls target the constructor and implicit ones the class`() {
        Edges.assertHas(
            edges,
            "rel/lang/Child# calls -> rel/lang/Base#`<init>`(Int).",
            "rel/lang/Child#`<init>`(). calls -> rel/lang/Child#`<init>`(Int,Int).",
            "rel/lang/User#child. calls -> rel/lang/Child#`<init>`().",
            "rel/lang/User#plain. calls/constructor -> rel/lang/Plain#",
            "rel/lang/Color#RED. calls -> rel/lang/Color#`<init>`(Int).",
        )
    }

    @Test
    fun `members the compiler generates are mapped to the declaration they come from`() {
        Edges.assertHas(
            edges,
            "$run calls/generated -> rel/lang/Point#",
            "$run calls/generated -> rel/lang/Color#",
            "$run calls/destructuring -> rel/lang/Point#x.",
            "$run calls/destructuring -> rel/lang/Point#y.",
            "$run references -> rel/lang/Color#RED.",
        )
    }

    @Test
    fun `named arguments of a constructor property refer to the property`() {
        Edges.assertHas(
            edges,
            "$run references/named-argument -> rel/lang/Point#x.",
            "$run references/named-argument -> rel/lang/Point#y.",
        )
    }

    @Test
    fun `operators indexing and iteration are calls of the operator functions`() {
        Edges.assertHas(
            edges,
            "$run calls -> rel/lang/Box#get(Int).",
            "$run calls -> rel/lang/Box#plus(Box).",
            "$run calls/iteration -> rel/lang/Box#iterator().",
        )
    }

    @Test
    fun `locals and parameters produce no relationships`() {
        val parameterOnly = edges.filter { it.startsWith("rel/lang/User#parameterOnly(Int).") }
        assertEquals(emptyList(), parameterOnly)
        assertTrue(edges.none { it.contains("doubled") || it.contains("inner") || it.contains("#local") })
        assertEquals(emptyList(), edges.filter { it.startsWith("rel/lang/Backed#") })
    }

    @Test
    fun `calling a property that has an invoke operator calls invoke and reads the property`() {
        val fire = "rel/lang/Trigger#fire()."
        Edges.assertHas(
            edges,
            "$fire calls -> rel/lang/Invokable#invoke().",
            "$fire references/read -> rel/lang/Trigger#invokable.",
            "$fire references/read -> rel/lang/Trigger#action.",
        )
    }
}
