package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.KrosScope

internal class ScanDirtyPaths(private val executable: String = "git", private val bound: Int = DEFAULT_BOUND) {
    fun of(checkouts: List<Checkout>): Map<String, DirtyPaths> {
        val git = GitChanges(executable, StartedProcesses())
        return checkouts.filter { it.head != null }
            .mapNotNull { checkout -> git.status(checkout.root)?.let { checkout.id to recorded(it) } }
            .toMap()
    }

    private fun recorded(status: GitStatus): DirtyPaths =
        if (status.paths.size > bound) DirtyPaths(exceeded = true) else DirtyPaths(paths = status.paths.sorted())

    companion object {
        const val DEFAULT_BOUND = 10_000
    }
}

internal fun KrosScope.dirtyPathsAtScan(): Map<String, DirtyPaths> =
    if (isIndexed()) ScanDirtyPaths().of(checkouts(null)) else emptyMap()
