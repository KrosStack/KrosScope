package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.lookup.repositoryAt

public fun KrosScope.freshnessInputs(folder: String? = null): FreshnessInputs =
    FreshnessInputs(checkouts(folder), ScanTimeFile(indexDirectory).read(), freshnessLimitMillis)

public fun KrosScope.freshness(folder: String? = null): IndexFreshness = freshnessInputs(folder).check()

internal fun KrosScope.checkouts(folder: String?): List<Checkout> {
    val asked = repositoryAt(folder.orEmpty())?.id
    return checkoutsOf(graph().structure().repositories) { it.id == asked }
}

internal fun KrosScope.checkoutsOf(repositories: List<Repository>, tree: (Repository) -> Boolean): List<Checkout> {
    val external = externalCheckouts.locate(repositories)
    return repositories.map { repository ->
        val root = external[repository.id] ?: workspaceRoot.resolve(repository.directory.value)
        Checkout(root, repository.headRevision, tree(repository), repository.id.value)
    }
}
