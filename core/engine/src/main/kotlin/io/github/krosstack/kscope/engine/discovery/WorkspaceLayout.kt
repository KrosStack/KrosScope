package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Path

/**
 * The repositories of a workspace root in the byte order of their directories, and what was found while resolving
 * them. [candidates] holds the directories below the root that a plain root was searched for checkouts in; it is null
 * when the root is itself in a work tree and nothing was searched. [withoutCommit] are the checkouts left out for
 * lacking a commit, which a later commit turns into repositories. [registrations] say which registered repositories
 * the layout was resolved with.
 */
internal class WorkspaceLayout(
    val repositories: List<WorkspaceRepository>,
    val notices: List<Diagnostic>,
    val candidates: List<String>?,
    val withoutCommit: List<String> = emptyList(),
    val registrations: Registrations = Registrations(),
) {
    /** The checkout directory, by repository id, of each repository of the layout that came from a registration. */
    fun registeredCheckouts(): Map<RepositoryId, Path> = repositories
        .filter { repository ->
            registrations.requested.any { it.name == repository.directory.value && it.root == repository.root }
        }
        .associate { it.repository.id to it.root }
}

/**
 * The registered repositories that a layout was resolved with, so that a changed list resolves it again, and those of
 * them left out for lacking a work tree or a commit, which restoring them turns into repositories. [blocked] are the
 * entries of the root whose names kept registered repositories out, which removing them lets in.
 */
internal class Registrations(
    val requested: List<RegisteredRepository> = emptyList(),
    val unavailable: List<RegisteredRepository> = emptyList(),
    val blocked: List<BlockingEntry> = emptyList(),
)

/** An entry of the workspace root as it was when it took the name of a registered repository. */
internal class BlockingEntry(val path: Path, val directory: Boolean)
