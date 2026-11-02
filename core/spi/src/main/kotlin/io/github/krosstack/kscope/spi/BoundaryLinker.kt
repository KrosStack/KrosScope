package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.spi.graph.GraphReader

public interface BoundaryLinker {
    public val descriptor: ProducerDescriptor

    /**
     * The namespaces of symbolic references that this linker answers. For each reference in them, [link] emits exactly
     * one relationship built with `SymbolicReference.answeredBy`, so that the engine can account for every reference.
     * A namespace belongs to one linker.
     */
    public val namespaces: Set<String> get() = emptySet()

    public fun link(graph: GraphReader, sink: FactSink)
}
