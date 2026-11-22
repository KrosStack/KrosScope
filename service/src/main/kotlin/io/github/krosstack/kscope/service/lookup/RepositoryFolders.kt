package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.service.KrosScope

internal fun Repository.listedName(): String = directory.value.ifEmpty { name }

internal fun Repository.asNarrowing(): RepositoryNarrowing =
    RepositoryNarrowing(id, listedName(), directory.value.isEmpty())

public fun KrosScope.repositoryHolding(folder: String): RepositoryId? = repositoryAt(folder)?.id

internal fun KrosScope.repositoryAt(folder: String): Repository? = graph().structure().repositories
    .filter { holds(it.directory.value, folder) }
    .maxByOrNull { it.directory.value.length }

private fun holds(directory: String, folder: String): Boolean =
    directory.isEmpty() || folder == directory || folder.startsWith("$directory/")
