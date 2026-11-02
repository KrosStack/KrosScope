package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.spi.graph.CoverageCell
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.store.codec.ResolutionStates
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.search.SearchLayout
import io.github.krosstack.kscope.store.search.SearchLayouts
import io.github.krosstack.kscope.store.sqlite.Database

internal class CoverageReader(private val database: Database) {
    fun coverage(): CoverageReport = CoverageReport(
        cells = cells(),
        artifactsByType = countsByText(
            "SELECT type_id, COUNT(*) FROM (SELECT DISTINCT repository, path, type_id FROM artifact) GROUP BY type_id",
        ),
        symbolsByKind = countsByText("SELECT kind_id, COUNT(DISTINCT id) FROM symbol GROUP BY kind_id"),
        metricsByName = countsByText(
            "SELECT name, COUNT(*) FROM (SELECT DISTINCT node_kind, node_key, name FROM metric) GROUP BY name",
        ),
        searchDocuments = SearchLayouts.all.associate { it.scope.name.lowercase() to documentCount(it) }
            .filterValues { it > 0 }
            .toSortedMap(),
    )

    private fun documentCount(layout: SearchLayout): Long =
        database.query("SELECT COUNT(*) FROM ${layout.documents}") { it.long() }.single()

    private fun cells(): List<CoverageCell> = database.query(
        "SELECT kind, evidence_class, res_state, COUNT(*) FROM relationship GROUP BY kind, evidence_class, res_state",
    ) { cursor ->
        CoverageCell(
            kind = cursor.enum(Wires.relationKind),
            evidenceClass = cursor.enum(Wires.evidenceClass),
            state = cursor.text(),
            count = cursor.long(),
        )
    }.sortedWith(compareBy(CoverageCell::kind, CoverageCell::evidenceClass, ::stateRank))

    private fun stateRank(cell: CoverageCell): Int = ResolutionStates.inReportingOrder.indexOf(cell.state)

    private fun countsByText(sql: String): Map<String, Long> =
        database.query(sql) { it.text() to it.long() }.toMap().toSortedMap()
}
