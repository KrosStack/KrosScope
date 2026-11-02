package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.service.RepositoryEntry
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal fun WorkspaceStructure.repositoryEntries(): List<RepositoryEntry> {
    if (repositories.size < 2) return emptyList()
    val repositoryOfRoot = buildRoots.associate { it.id to it.repository }
    val modulesOf = modules.groupingBy { repositoryOfRoot[it.buildRoot] }.eachCount()
    return repositories.sortedWith(compareBy({ it.directory.value }, { it.id.value })).map {
        RepositoryEntry(it.id.value, it.name, it.directory.value, it.headRevision, modulesOf[it.id] ?: 0)
    }
}
