package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationSamples
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportRelationshipsTest {
    private val batches by lazy { analyzeAll(RelationSamples.imports) }
    private val edges: List<String> by lazy { Edges("imports@main rel/imports/").of(batches) }
    private val kotlinFile = "file:relations/imports/main/kotlin/rel/imports/Consumer.kt"
    private val javaFile = "file:relations/imports/main/java/rel/imports/JavaConsumer.java"

    @Test
    fun `an import of a declaration refers to it and an alias does not change the target`() {
        Edges.assertHas(
            edges,
            "$kotlinFile imports -> helpers/help().",
            "$kotlinFile imports -> helpers/help(String).",
            "$javaFile imports -> helpers/Tool#",
        )
    }

    @Test
    fun `an overloaded name is imported once per overload`() {
        Edges.assertHas(
            edges,
            "$kotlinFile imports -> helpers/help().",
            "$kotlinFile imports -> helpers/help(String).",
            "$javaFile imports -> helpers/help().",
            "$javaFile imports -> helpers/help(String).",
        )
    }

    @Test
    fun `a star import of a class refers to the class and a star import of a package to nothing`() {
        val kotlinImports = edges.filter { it.startsWith(kotlinFile) }
        Edges.assertHas(edges, "$kotlinFile imports -> helpers/Constants#", "$javaFile imports -> helpers/Constants#")
        assertEquals(3, kotlinImports.count { "imports -> helpers/" in it })
        assertEquals(0, edges.count { "java/util" in it || "kotlin/collections" in it })
    }

    @Test
    fun `a star import of a package that does not exist is unresolved`() {
        Edges.assertHas(
            edges,
            "$kotlinFile imports -> ?rel.imports.nowhere (kotlin.unresolved-reference.no-classpath)",
        )
    }

    @Test
    fun `names that an import brings into scope resolve to their declarations`() {
        Edges.assertHas(
            edges,
            "Consumer#use(). calls -> helpers/help().",
            "Consumer#use(). references/read -> helpers/Constants#SIZE.",
            "Consumer#use(). calls -> helpers/Constants#make().",
            "JavaConsumer#use(). calls -> helpers/help(String).",
            "JavaConsumer#use(). references/read -> helpers/Constants#SIZE.",
        )
    }
}
