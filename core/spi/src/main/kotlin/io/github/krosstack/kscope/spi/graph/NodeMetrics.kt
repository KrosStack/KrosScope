package io.github.krosstack.kscope.spi.graph

import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.NodeRef

/** The numeric properties of nodes. A graph that records none answers with empty lists. */
public interface NodeMetrics {
    /** The metrics of one [node], in canonical order. */
    public fun metrics(node: NodeRef): List<Metric> = emptyList()

    /** Every metric called [name], for all nodes, in canonical order. */
    public fun metrics(name: String): List<Metric> = emptyList()
}
