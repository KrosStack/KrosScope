package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.RelationKind

/**
 * A Kotlin `external` declaration and the native function in another repository that implements it. The declaration
 * generates a JNI function symbol and that symbol is bound to the native function, so the link is read over both hops.
 */
internal class NativeBindingLinks : LinkProducer {
    override fun produce(graph: WorkspaceGraph): List<ProducedLink> =
        graph.relationships(RelationKind.BINDS, null).flatMap { binding ->
            val function = graph.at(graph.symbol(graph.target(binding)))
            val generations = graph.reader.incoming(binding.from, setOf(RelationKind.GENERATES))
            generations.mapNotNull { generation ->
                val declaration = graph.at(graph.symbol(generation.from))
                if (declaration == null || function == null) {
                    null
                } else {
                    ProducedLink(declaration, function, binding.evidenceClass.wireName())
                }
            }
        }.filter { it.crossesRepositories }
}
