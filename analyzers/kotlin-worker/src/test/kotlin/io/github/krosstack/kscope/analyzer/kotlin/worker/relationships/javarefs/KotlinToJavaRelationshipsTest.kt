package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.RelationFacts
import kotlin.test.Test

class KotlinToJavaRelationshipsTest {
    private val edges = RelationFacts.mixedEdges
    private val kotlinUse = "rel/mixed/KotlinUser#use()."

    @Test
    fun `Kotlin reaches Java constructors statics fields and overloads`() {
        Edges.assertHas(
            edges,
            "$kotlinUse calls -> rel/mixed/JavaSupport#`<init>`(String).",
            "$kotlinUse calls/constructor -> rel/mixed/JavaPlain#",
            "$kotlinUse calls -> rel/mixed/JavaSupport#shout(String).",
            "$kotlinUse calls -> rel/mixed/JavaSupport#shout(int).",
            "$kotlinUse references/read -> rel/mixed/JavaSupport#MAX.",
            "$kotlinUse references/write -> rel/mixed/JavaSupport#limit.",
            "$kotlinUse references/read -> rel/mixed/JavaSupport#limit.",
            "$kotlinUse calls -> rel/mixed/JavaPlain#size().",
        )
    }

    @Test
    fun `Kotlin synthetic properties are calls of the Java getter and setter`() {
        Edges.assertHas(
            edges,
            "$kotlinUse calls/read -> rel/mixed/JavaSupport#getName().",
            "$kotlinUse calls/write -> rel/mixed/JavaSupport#setName(String).",
        )
    }

    @Test
    fun `Kotlin lambdas and SAM constructors for Java interfaces refer to the Java declarations`() {
        Edges.assertHas(
            edges,
            "$kotlinUse calls -> rel/mixed/JavaSupport#onDone(JavaCallback).",
            "$kotlinUse calls/sam-constructor -> rel/mixed/JavaCallback#",
        )
    }

    @Test
    fun `Kotlin extending a Java class and implementing a Java interface overrides the Java members`() {
        Edges.assertHas(
            edges,
            "rel/mixed/KotlinUser# extends -> rel/mixed/JavaBase#",
            "rel/mixed/KotlinUser# calls -> rel/mixed/JavaBase#`<init>`(String).",
            "rel/mixed/KotlinUser# implements -> rel/mixed/JavaListener#",
            "rel/mixed/KotlinUser#describe(). overrides -> rel/mixed/JavaBase#describe().",
            "rel/mixed/KotlinUser#listen(String). overrides -> rel/mixed/JavaListener#listen(String).",
        )
    }

    @Test
    fun `Kotlin reaches a Java record through its implicit constructor and accessors`() {
        Edges.assertHas(
            edges,
            "$kotlinUse calls/constructor -> rel/mixed/JavaPair#",
            "$kotlinUse calls -> rel/mixed/JavaPair#left.",
            "$kotlinUse calls -> rel/mixed/JavaPair#right.",
        )
    }
}
