package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * The repository id is the Git root commit when there is one. A checkout of a workspace root that is walked as one
 * repository else gets a random id kept next to the index. [directory] is where the checkout lies below the root.
 */
internal class RepositoryIdentity(
    private val checkoutRoot: Path,
    private val indexDirectory: Path,
    private val directory: RepoPath = RepoPath(""),
) {
    fun resolve(git: GitAccess): Repository {
        val workTree = git as? GitAccess.WorkTree
        return describe(workTree, workTree?.rootCommit() ?: persistedId())
    }

    /** Null when the checkout has no commit, so that it has no id of its own. */
    fun resolveCommitted(workTree: GitAccess.WorkTree): Repository? =
        workTree.rootCommit()?.let { describe(workTree, it) }

    private fun describe(workTree: GitAccess.WorkTree?, id: String): Repository {
        val name = checkoutRoot.toAbsolutePath().normalize().fileName?.toString() ?: DEFAULT_NAME
        return Repository(RepositoryId(id), name, workTree?.headRevision(), originUrl = null, directory = directory)
    }

    private fun persistedId(): String {
        val file = indexDirectory.resolve(ID_FILE)
        val existing = if (Files.isRegularFile(file)) Files.readString(file).trim() else ""
        if (existing.matches(WELL_FORMED_ID)) return existing
        val created = UUID.randomUUID().toString()
        Files.createDirectories(indexDirectory)
        Files.writeString(file, created + "\n")
        return created
    }

    private companion object {
        const val ID_FILE = "repository-id"
        const val DEFAULT_NAME = "workspace"
        val WELL_FORMED_ID = Regex("[A-Za-z0-9._-]+")
    }
}
