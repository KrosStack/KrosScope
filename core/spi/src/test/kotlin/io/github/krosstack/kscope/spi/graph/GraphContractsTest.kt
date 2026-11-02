package io.github.krosstack.kscope.spi.graph

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphContractsTest {
    @Test
    fun `a coverage report survives a JSON round trip`() {
        val report = CoverageReport(
            cells = listOf(CoverageCell(RelationKind.CALLS, EvidenceClass.COMPILER, "exact", 7)),
            artifactsByType = mapOf("kotlin.source" to 3),
            symbolsByKind = mapOf("kotlin.class" to 5),
            metricsByName = mapOf("history.commits" to 4),
            searchDocuments = mapOf("symbols" to 5, "artifacts" to 3),
        )
        val encoded = KscopeJson.encodeToString(CoverageReport.serializer(), report)
        assertEquals(report, KscopeJson.decodeFromString(CoverageReport.serializer(), encoded))
    }

    @Test
    fun `a graph that records no metrics answers with empty lists`() {
        val none = object : NodeMetrics {}
        assertEquals(emptyList(), none.metrics(NodeRef.OfExternal("maven:g:a")))
        assertEquals(emptyList(), none.metrics("history.commits"))
    }

    @Test
    fun `a symbol query matches everything by default`() {
        val query = SymbolQuery()
        assertNull(query.name)
        assertNull(query.artifact)
        assertEquals(1000, query.limit)
    }

    @Test
    fun `a relationship query matches every kind by default`() {
        val query = RelationshipQuery()
        assertTrue(query.kinds.isEmpty())
        assertNull(query.resolved)
        assertEquals(100_000, query.limit)
    }
}
