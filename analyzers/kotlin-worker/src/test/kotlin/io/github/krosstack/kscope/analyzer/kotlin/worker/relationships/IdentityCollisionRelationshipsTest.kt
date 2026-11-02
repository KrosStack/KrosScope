package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Declarations that share one identity are recorded once, so a relationship that names the identity cannot say which
 * declaration it means: it must not be exact.
 */
class IdentityCollisionRelationshipsTest {
    private val module = SampleWorkspace.singleModule("collisions", "relations/collisions/main")
    private val batches by lazy { analyzeAll(module) }
    private val edges by lazy { Edges("collisions@main ").of(batches) }
    private val collision = "identity.collision"

    @Test
    fun `overloads that differ only by nullability share one identity and are reported`() {
        val picks = batches.symbols().filter { it.qualifiedName == "rel.collisions.pick" }
        assertEquals(listOf("rel/collisions/pick(Int)."), picks.map { Edges.text(it.id).substringAfter(" ") })
        val reported = batches.diagnostics().filter { it.code.value == collision }.map { it.message }
        assertTrue(reported.any { it.startsWith("rel.collisions.pick ") }, reported.toString())
    }

    @Test
    fun `a call of an overload whose identity is shared is not exact`() {
        Edges.assertHas(
            edges,
            "rel/collisions/callsNull(). calls -> ?pick ($collision)",
            "rel/collisions/callsInt(). calls -> ?pick ($collision)",
        )
        Edges.assertLacks(
            edges,
            "rel/collisions/callsNull(). calls -> rel/collisions/pick(Int).",
            "rel/collisions/callsInt(). calls -> rel/collisions/pick(Int).",
        )
    }

    @Test
    fun `what a declaration with a shared identity refers to is not attributed to the declaration`() {
        assertTrue(edges.none { it.startsWith("rel/collisions/pick(Int). ") }, edges.toString())
        val file = "file:relations/collisions/main/kotlin/rel/collisions/Nullable.kt"
        Edges.assertHas(
            edges,
            "$file calls -> rel/collisions/plainSide().",
            "$file calls -> rel/collisions/nullableSide().",
        )
    }

    @Test
    fun `references to declarations with a unique identity stay exact`() {
        Edges.assertHas(
            edges,
            "rel/collisions/callsUnique(). calls -> rel/collisions/unique(Int).",
            "rel/collisions/unique(Int). calls -> rel/collisions/plainSide().",
        )
    }

    @Test
    fun `java overloads of a primitive and its box have distinct identities and exact calls`() {
        val boxes = "rel/collisions/Boxes#"
        Edges.assertHas(
            edges,
            "${boxes}callsPrimitive(). calls -> ${boxes}take(int).",
            "${boxes}callsBoxed(). calls -> ${boxes}take(Integer).",
        )
        val reported = batches.diagnostics().filter { it.code.value == collision }.map { it.message }
        assertTrue(reported.none { it.contains(".take ") }, reported.toString())
    }

    @Test
    fun `java overloads of types with one simple name share an identity and a call of them is not exact`() {
        val boxes = "rel/collisions/Boxes#"
        Edges.assertHas(edges, "${boxes}callsJoin(). calls -> ?join ($collision)")
        Edges.assertLacks(edges, "${boxes}callsJoin(). calls -> ${boxes}join(Item).")
    }

    @Test
    fun `a module that is analysed first does not name a shared identity of its dependency as exact`() {
        val app = SampleWorkspace.module(
            "app",
            Platform.JVM,
            SampleWorkspace.sourceSet("app", "main", "relations/collisions-app/main"),
        ).copy(dependencies = listOf("collisions"))
        val appEdges = Edges("app@main ").of(analyzeAll(app, module))
        val file = "file:relations/collisions-app/main/kotlin/rel/collisions/app/Caller.kt"
        Edges.assertHas(
            appEdges,
            "rel/collisions/app/callsAcrossModules(). calls -> ?pick ($collision)",
            "rel/collisions/app/callsAcrossModules(). calls -> collisions@main rel/collisions/unique(Int).",
            "$file imports -> ?rel.collisions.pick ($collision)",
            "$file imports -> collisions@main rel/collisions/unique(Int).",
        )
        assertTrue(appEdges.none { it.endsWith("-> collisions@main rel/collisions/pick(Int).") }, appEdges.toString())
    }
}
