package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.RelationFacts
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaToKotlinRelationshipsTest {
    private val edges = RelationFacts.mixedEdges
    private val javaCall = "rel/mixed/JavaCaller#call(List)."

    @Test
    fun `Java reaches Kotlin constructors through the light classes`() {
        Edges.assertHas(
            edges,
            "$javaCall calls -> rel/mixed/Service#`<init>`(String,Int).",
            "$javaCall calls -> rel/mixed/Service#`<init>`().",
            "$javaCall calls/constructor -> rel/mixed/Implicit#",
        )
    }

    @Test
    fun `Java reaches Kotlin properties through their accessors`() {
        Edges.assertHas(
            edges,
            "$javaCall calls -> rel/mixed/Service#label.",
            "$javaCall calls -> rel/mixed/Service#counter.",
        )
    }

    @Test
    fun `Java reaches top level Kotlin functions through the file facade`() {
        Edges.assertHas(
            edges,
            "$javaCall calls -> rel/mixed/twice(Int).",
            "$javaCall calls -> rel/mixed/extra(this:String).",
        )
    }

    @Test
    fun `Java naming the file facade of a Kotlin file references that file`() {
        val facade = "$javaCall references -> file:relations/mixed/main/kotlin/rel/mixed/Api.kt"
        assertEquals(2, edges.count { it == facade }, "Expected the two facade qualifiers in $edges")
    }

    @Test
    fun `Java reaches JvmStatic members companion members objects constants and enum entries`() {
        Edges.assertHas(
            edges,
            "$javaCall calls -> rel/mixed/Service#Companion#create().",
            "$javaCall calls -> rel/mixed/Service#Companion#instance().",
            "$javaCall references -> rel/mixed/Service#Companion#",
            "$javaCall references/read -> rel/mixed/Service#Companion#LIMIT.",
            "$javaCall calls -> rel/mixed/Registry#register(String).",
            "$javaCall references -> rel/mixed/Registry#",
            "$javaCall references -> rel/mixed/Mode#FAST.",
        )
    }

    @Test
    fun `Java selects the overload of a Kotlin function`() {
        Edges.assertHas(
            edges,
            "$javaCall calls -> rel/mixed/Service#run(String).",
            "$javaCall calls -> rel/mixed/Service#run(Int).",
        )
    }

    @Test
    fun `Java implementing a Kotlin interface and anonymous classes are inheritance relationships`() {
        Edges.assertHas(
            edges,
            "rel/mixed/JavaCaller# implements -> rel/mixed/Handler#",
            "rel/mixed/JavaCaller#handle(String). overrides -> rel/mixed/Handler#handle(String).",
            "$javaCall implements/anonymous -> jdk java/lang/Runnable#",
        )
    }

    @Test
    fun `Java extending a Java class and overriding its methods stays Java to Java`() {
        Edges.assertHas(
            edges,
            "rel/mixed/JavaSupport# extends -> rel/mixed/JavaBase#",
            "rel/mixed/JavaSupport# implements -> rel/mixed/JavaListener#",
            "rel/mixed/JavaSupport#describe(). overrides -> rel/mixed/JavaBase#describe().",
            "rel/mixed/JavaSupport#`<init>`(String). calls -> rel/mixed/JavaBase#`<init>`(String).",
        )
    }

    @Test
    fun `Java field reads and writes are told apart`() {
        Edges.assertHas(
            edges,
            "rel/mixed/JavaSupport#getName(). references/read -> rel/mixed/JavaSupport#name.",
            "rel/mixed/JavaSupport#setName(String). references/write -> rel/mixed/JavaSupport#name.",
        )
    }
}
