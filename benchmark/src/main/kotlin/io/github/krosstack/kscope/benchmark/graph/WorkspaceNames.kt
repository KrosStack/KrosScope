package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.graph.GraphReader

internal class WorkspaceNames(private val directories: Map<RepositoryId, String> = emptyMap()) {
    fun of(artifact: ArtifactRef): String {
        val directory = directories[artifact.repository].orEmpty()
        return if (directory.isEmpty()) artifact.path.value else "$directory/${artifact.path.value}"
    }

    companion object {
        val SINGLE = WorkspaceNames()

        fun of(graph: GraphReader): WorkspaceNames =
            WorkspaceNames(graph.structure().repositories.associate { it.id to it.directory.value })
    }
}
