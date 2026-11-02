package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Files
import java.nio.file.Path

/**
 * The directories outside the workspace at [workspace] that its files may be read from: the checkouts that the last
 * scan indexed, kept in [scanned], and the repositories that the user has registered now, kept in [registrations].
 * Both are files of the user's machine; nothing in the workspace or its index is trusted to name a directory.
 */
internal class ExternalCheckouts(
    private val workspace: Path,
    private val registrations: RepositoryRegistry,
    private val scanned: ScannedCheckouts,
) {
    /** Remembers the checkouts that a scan indexed until the next scan; a record that cannot be written is skipped. */
    fun accepted(checkouts: Map<RepositoryId, Path>) {
        scanned.save(workspace, checkouts)
    }

    /**
     * The directory to read each of [repositories] from when it is not below the workspace root. A repository of the
     * last scan has the checkout that the scan found; one that was registered since is found by its name, unless a
     * directory of the root has that name.
     */
    fun locate(repositories: List<Repository>): Map<RepositoryId, Path> {
        val indexed = scanned.read(workspace)
        val registered = registrations.registered(workspace).associate { it.name to it.root }
        return repositories.mapNotNull { repository ->
            val checkout = indexed[repository.id] ?: registeredBy(repository, registered)
            checkout?.let { repository.id to it }
        }.toMap()
    }

    private fun registeredBy(repository: Repository, registered: Map<String, Path>): Path? {
        val name = repository.directory.value
        return registered[name]?.takeIf { name.isNotEmpty() && !Files.exists(workspace.resolve(name)) }
    }
}
