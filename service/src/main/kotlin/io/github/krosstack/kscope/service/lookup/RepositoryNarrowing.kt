package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.service.KrosScope

public data class RepositoryNarrowing(
    public val repository: RepositoryId,
    public val name: String,
    public val atRoot: Boolean,
)

public fun KrosScope.narrowingFor(folder: String?): RepositoryNarrowing? {
    if (folder == null || graph().structure().repositories.size < 2) return null
    return repositoryAt(folder)?.asNarrowing()
}
