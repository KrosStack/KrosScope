package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.Golden
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The compiler makes `equals` for data and value classes, so `==` on them calls nothing that the source declares. An
 * `equals` that a class declares is called as any other function.
 */
class EqualityOperatorRelationshipsTest {
    private val batches by lazy { analyzeAll(listOf(RelationSamples.equality)) }
    private val edges by lazy { Edges("equality@main ").of(batches) }
    private val comparisons = "rel/equality/Comparisons#"

    private fun callsFrom(function: String): List<String> =
        edges.filter { it.startsWith("$comparisons$function") && " calls -> " in it }

    @Test
    fun `equality on a data class or a value class is no call`() {
        assertEquals(emptyList(), callsFrom("data("))
        assertEquals(emptyList(), callsFrom("dataNegated("))
        assertEquals(emptyList(), callsFrom("value("))
        assertTrue(edges.none { "generated" in it }, "$edges")
    }

    @Test
    fun `equality on a class with a declared equals calls it, also negated`() {
        Edges.assertHas(
            edges,
            "${comparisons}declared(Keyed,Keyed). calls -> rel/equality/Keyed#equals(Any).",
            "${comparisons}declaredNegated(Keyed,Keyed). calls -> rel/equality/Keyed#equals(Any).",
        )
    }

    @Test
    fun `equality on a class that inherits equals is not reported as a call into the workspace`() {
        assertEquals(emptyList(), callsFrom("inherited("))
    }

    @Test
    fun `the output matches the golden file`() =
        Golden.assertMatches("relationships-equality.jsonl", Golden.relationships(batches))
}
