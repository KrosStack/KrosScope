package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.store.codec.nodeRef
import io.github.krosstack.kscope.store.codec.nodeRefColumns
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn

internal object MetricTable : FactTable<Metric> {
    override val spec = TableSpec(
        name = "metric",
        columns = nodeRefColumns("node", required = true) + listOf("name TEXT NOT NULL", "value REAL NOT NULL"),
        indexes = listOf(indexOn("node_kind", "node_key"), indexOn("name")),
    )

    override fun bind(binder: Binder, value: Metric) = with(binder) {
        nodeRef(value.node)
        text(value.name)
        double(value.value)
    }

    override fun read(cursor: Cursor): Metric = with(cursor) {
        Metric(node = nodeRef(), name = text(), value = double())
    }

    override fun wrap(value: Metric): Fact = Fact.OfMetric(value)

    override fun unwrap(fact: Fact): Metric? = (fact as? Fact.OfMetric)?.value
}
