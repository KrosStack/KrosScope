package io.github.krosstack.kscope.benchmark.workspace

/** Reads the links of one kind between repositories from a graph. */
internal interface LinkProducer {
    fun produce(graph: WorkspaceGraph): List<ProducedLink>
}
