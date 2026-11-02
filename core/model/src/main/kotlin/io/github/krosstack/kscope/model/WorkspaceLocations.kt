package io.github.krosstack.kscope.model

/**
 * Names the files of a workspace the way a person types them. A workspace path is the directory of the repository, a
 * slash and the repository-relative path; when the workspace has one repository the directory is empty and a
 * workspace path is the repository-relative path itself. Nothing here is stored.
 */
public class WorkspaceLocations(repositories: Collection<Repository>) {
    private val directories: List<Pair<RepositoryId, String>> = repositories
        .map { it.id to it.directory.value }
        .distinct()
        .sortedWith(compareBy({ it.second }, { it.first.value }))
    private val directoryOf: Map<RepositoryId, String> = directories.toMap()

    public val severalRepositories: Boolean = directories.any { it.second.isNotEmpty() }

    public fun pathOf(artifact: ArtifactRef): String =
        prefixed(directoryOf[artifact.repository].orEmpty(), artifact.path)

    public fun directoryOf(repository: RepositoryId): String = directoryOf[repository].orEmpty()

    /** The files that [workspacePath] can name, one per repository whose directory starts it; they may not exist. */
    public fun artifactsNamed(workspacePath: String): List<ArtifactRef> = directories.mapNotNull { (id, directory) ->
        val inside = if (directory.isEmpty()) workspacePath else workspacePath.removePrefix("$directory/")
        val named = directory.isEmpty() || inside != workspacePath
        inside.takeIf { named }?.let { path -> runCatching { RepoPath(path) }.getOrNull() }?.let { ArtifactRef(id, it) }
    }

    /** [path] in each repository, which is how a path that leaves out the directory would read; they may not exist. */
    public fun withinEachRepository(path: String): List<ArtifactRef> {
        val relative = runCatching { RepoPath(path) }.getOrNull() ?: return emptyList()
        return directories.map { ArtifactRef(it.first, relative) }
    }

    private fun prefixed(directory: String, path: RepoPath): String =
        if (directory.isEmpty()) path.value else "$directory/${path.value}"

    public companion object {
        public val SINGLE: WorkspaceLocations = WorkspaceLocations(emptyList())
    }
}
