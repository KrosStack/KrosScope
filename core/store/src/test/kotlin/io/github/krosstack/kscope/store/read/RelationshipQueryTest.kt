package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.read.EdgeFixtures.all
import io.github.krosstack.kscope.store.read.EdgeFixtures.extendsEdge
import io.github.krosstack.kscope.store.read.EdgeFixtures.probableCall
import io.github.krosstack.kscope.store.read.EdgeFixtures.resolvedCall
import io.github.krosstack.kscope.store.read.EdgeFixtures.unanalysedImport
import io.github.krosstack.kscope.store.read.EdgeFixtures.unresolvedCall
import io.github.krosstack.kscope.store.read.EdgeFixtures.withEdges
import kotlin.test.Test
import kotlin.test.assertEquals

class RelationshipQueryTest {
    @Test
    fun `relationships are filtered by kind`() = withEdges { store ->
        val query = RelationshipQuery(kinds = setOf(RelationKind.EXTENDS))
        assertEquals(listOf(extendsEdge), store.relationships(query))
    }

    @Test
    fun `relationships are filtered by sub kind and evidence class`() = withEdges { store ->
        assertEquals(listOf(probableCall), store.relationships(RelationshipQuery(subKind = "kotlin.invoke")))
        val textual = RelationshipQuery(evidenceClass = EvidenceClass.TEXTUAL)
        assertEquals(listOf(unanalysedImport), store.relationships(textual))
    }

    @Test
    fun `relationships are filtered by whether they are resolved`() = withEdges { store ->
        assertEquals(
            setOf(resolvedCall, probableCall, extendsEdge),
            store.relationships(RelationshipQuery(resolved = true)).toSet(),
        )
        assertEquals(
            setOf(unresolvedCall, unanalysedImport),
            store.relationships(RelationshipQuery(resolved = false)).toSet(),
        )
    }

    @Test
    fun `an empty query returns all relationships and the limit truncates after ordering`() = withEdges { store ->
        val everything = store.relationships(RelationshipQuery())
        assertEquals(all.toSet(), everything.toSet())
        assertEquals(everything.take(2), store.relationships(RelationshipQuery(limit = 2)))
    }

    @Test
    fun `relationship order does not depend on insertion order`() {
        val forward = SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.edgeBatch("unit", all))
            store.relationships(RelationshipQuery())
        }
        val backward = SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.edgeBatch("unit", all.reversed()))
            store.relationships(RelationshipQuery())
        }
        assertEquals(forward, backward)
    }
}
