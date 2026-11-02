package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import kotlin.test.Test

/** Names that the compiler reads as one declaration and the source means as another. */
class KotlinNameMappingTest {
    private val edges = RelationFacts.langEdges
    private val located = RelationFacts.langLocatedEdges
    private val run = "rel/lang/User#run(Point,Box)."

    @Test
    fun `a class name that stands for its companion references the class and a written Companion the object`() {
        val member = "rel/lang/User#companionQualifiers()."
        Edges.assertHas(
            located,
            "$member references -> rel/lang/Counter# @51:21",
            "$member references -> rel/lang/Counter# @52:20",
            "$member references -> rel/lang/Counter# @53:24",
            "$member references -> rel/lang/Counter#Companion# @53:32",
            "$member calls -> rel/lang/Counter#Companion#make(). @52:28",
        )
        Edges.assertLacks(
            located,
            "$member references -> rel/lang/Counter#Companion# @51:21",
            "$member references -> rel/lang/Counter#Companion# @52:20",
        )
    }

    @Test
    fun `a constructor parameter that declares a property is that property wherever the compiler reads it`() {
        Edges.assertHas(edges, "rel/lang/Holder# references/read -> rel/lang/Holder#base.")
    }

    @Test
    fun `the named arguments of copy are the properties of the data class`() {
        Edges.assertHas(located, "$run references/named-argument -> rel/lang/Point#x. @11:31")
    }
}
