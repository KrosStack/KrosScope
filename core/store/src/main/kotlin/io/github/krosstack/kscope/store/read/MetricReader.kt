package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.spi.graph.NodeMetrics
import io.github.krosstack.kscope.store.codec.NodeRefs
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.WhereClause
import io.github.krosstack.kscope.store.table.MetricTable
import io.github.krosstack.kscope.store.table.select

internal class MetricReader(private val database: Database) : NodeMetrics {
    override fun metrics(node: NodeRef): List<Metric> = find(
        WhereClause().equal("node_kind", NodeRefs.kindOf(node)).equal("node_key", NodeRefs.keyOf(node)),
    )

    override fun metrics(name: String): List<Metric> = find(WhereClause().equal("name", name))

    private fun find(where: WhereClause): List<Metric> =
        CanonicalOrder.sorted(database.select(MetricTable, where).distinct(), MetricTable::wrap)
}
