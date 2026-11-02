package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.RelationKind

/** A module that depends on a module of another repository, through a dependency of the given sub-kind. */
internal class ModuleDependencyLinks(private val subKind: String) : LinkProducer {
    override fun produce(graph: WorkspaceGraph): List<ProducedLink> =
        graph.relationships(RelationKind.DEPENDS_ON, subKind).mapNotNull { relationship ->
            val from = graph.moduleEnd(relationship.from)
            val to = graph.moduleEnd(graph.target(relationship))
            if (from == null || to == null) null else ProducedLink(from, to, relationship.evidenceClass.wireName())
        }.filter { it.crossesRepositories }
}
