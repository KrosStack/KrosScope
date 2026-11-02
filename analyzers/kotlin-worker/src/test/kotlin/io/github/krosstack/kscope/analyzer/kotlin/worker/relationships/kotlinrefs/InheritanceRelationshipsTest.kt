package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationSamples
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolEcosystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InheritanceRelationshipsTest {
    private val batches by lazy { analyzeAll(RelationSamples.inheritance) }
    private val edges: List<String> by lazy { Edges("inherit-app@main ").of(batches) }
    private val lib = "inherit-lib@main rel/inherit/"
    private val app = "rel/inheritapp/"

    private fun targetId(relationship: Relationship) =
        ((relationship.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id

    @Test
    fun `a class in another module extends the abstract class and overrides across both levels`() {
        Edges.assertHas(
            edges,
            "${app}UserRepository# extends -> ${lib}BaseRepository#",
            "${app}UserRepository# calls/constructor -> ${lib}BaseRepository#",
            "${app}UserRepository#load(Int). overrides -> ${lib}Repository#load(Int).",
            "${app}UserRepository#describe(). overrides -> ${lib}BaseRepository#describe().",
        )
    }

    @Test
    fun `an interface implemented in the same module and by delegation is an implements relationship`() {
        Edges.assertHas(
            edges,
            "${lib}BaseRepository# implements -> ${lib}Repository#",
            "${app}Cached# implements -> ${lib}Repository#",
            "${lib}BaseRepository#count(). overrides -> ${lib}Repository#count().",
        )
    }

    @Test
    fun `an overriding constructor property overrides the open property of the base class`() {
        Edges.assertHas(
            edges,
            "${app}Person#name. overrides -> ${lib}Named#name.",
            "${app}Person# extends -> ${lib}Named#",
            "${app}Person# calls -> ${lib}Named#`<init>`(String).",
        )
    }

    @Test
    fun `an anonymous object implements its interface from the function that creates it`() {
        Edges.assertHas(edges, "${app}anonymous(). implements/anonymous -> ${lib}Repository#")
    }

    @Test
    fun `type arguments of supertypes are type uses`() {
        Edges.assertHas(edges, "${lib}Named# type-uses -> ${lib}Named#")
    }

    @Test
    fun `only inheritance relationships may point at library symbols and they name the library`() {
        val libraryEdges = batches.relationships().filter {
            val identity = (targetId(it)?.identity() as? ParsedSymbolId.Valid)?.identity
            identity != null && identity.ecosystem != SymbolEcosystem.GRADLE
        }
        val inheritance = setOf(RelationKind.EXTENDS, RelationKind.IMPLEMENTS, RelationKind.OVERRIDES)
        assertTrue(libraryEdges.isNotEmpty() && libraryEdges.all { it.kind in inheritance })
        val stdlib = "kscope maven org.jetbrains.kotlin:kotlin-stdlib . "
        assertEquals(
            listOf(
                "${stdlib}kotlin/Any#toString().",
                "${stdlib}kotlin/Comparable#",
                "${stdlib}kotlin/Comparable#compareTo(T).",
            ),
            libraryEdges.mapNotNull { targetId(it)?.value }.sorted(),
        )
    }
}
