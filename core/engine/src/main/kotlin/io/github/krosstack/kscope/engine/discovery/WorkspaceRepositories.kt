package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Path

/**
 * Finds the repositories of a workspace root. A root in a Git work tree is one repository, as is a root with fewer
 * than two checkouts directly below it, which is walked as it is. Otherwise every checkout directly below the root is
 * a repository, in the byte order of its directory, unless it has no commit or repeats the id of an earlier one.
 * Repositories that the user [registered] from elsewhere join either kind of root under their names.
 */
internal class WorkspaceRepositories(
    root: Path,
    indexDirectory: Path,
    private val runner: ProcessRunner = ProcessRunner(),
    private val executable: String = GitAccess.DEFAULT_EXECUTABLE,
    private val resolveDirectory: Boolean = false,
) {
    private val root = root.toAbsolutePath().normalize()
    private val indexDirectory = indexDirectory.toAbsolutePath().normalize()
    private val candidates = RepositoryCandidates(this.root, this.indexDirectory)

    fun candidateDirectories(): List<String> = candidates.names()

    fun hasCommit(name: String): Boolean = hasCommitAt(root.resolve(name))

    fun hasCommitAt(directory: Path): Boolean = (probe(directory) as? GitAccess.WorkTree)?.rootCommit() != null

    fun resolve(registered: List<RegisteredRepository> = emptyList()): WorkspaceLayout {
        val rootGit = probe(root)
        val local = if (rootGit !is GitAccess.NotARepository) {
            single(rootGit, null)
        } else {
            val names = candidates.names()
            val several = names.size >= MINIMUM_FOR_SEVERAL || (registered.isNotEmpty() && names.isNotEmpty())
            if (several) several(names) else single(rootGit, names)
        }
        if (registered.isEmpty()) return local
        return RegisteredCheckouts(root, indexDirectory, ::probe).join(local, registered)
    }

    private fun single(rootGit: GitAccess, names: List<String>?): WorkspaceLayout {
        val repository = WorkspaceRepository(root, RepoPath(""), rootGit) {
            RepositoryIdentity(root, indexDirectory).resolve(rootGit)
        }
        return WorkspaceLayout(listOf(repository), emptyList(), names)
    }

    private fun several(names: List<String>): WorkspaceLayout {
        val notices = mutableListOf<Diagnostic>()
        val kept = linkedMapOf<RepositoryId, String>()
        val missing = mutableListOf<String>()
        val repositories = names.mapNotNull { name ->
            val directory = root.resolve(name)
            val git = probe(directory) as? GitAccess.WorkTree ?: return@mapNotNull null
            val repository = RepositoryIdentity(directory, indexDirectory, RepoPath(name)).resolveCommitted(git)
            when {
                repository == null -> null.also {
                    missing += name
                    notices += withoutCommit(name)
                }

                repository.id in kept -> null.also { notices += duplicate(name, kept.getValue(repository.id)) }

                else -> {
                    kept[repository.id] = name
                    WorkspaceRepository(directory, RepoPath(name), git) { repository }
                }
            }
        }
        notices += listOfNotNull(FilesOutsideRepositories(root, indexDirectory).notice(names))
        return WorkspaceLayout(repositories, notices, names, missing)
    }

    private fun probe(directory: Path) = GitAccess.probe(directory, runner, executable, resolveDirectory)

    private fun withoutCommit(name: String) = Diagnostic(
        severity = DiagnosticSeverity.WARNING,
        code = ReasonCode("workspace.repository-without-commit"),
        message = "$name has no commit yet and is not indexed.",
        location = null,
    )

    private fun duplicate(name: String, keptName: String) = Diagnostic(
        severity = DiagnosticSeverity.WARNING,
        code = ReasonCode("workspace.duplicate-repository"),
        message = "$name has the same repository id as $keptName and is not indexed.",
        location = null,
    )

    private companion object {
        const val MINIMUM_FOR_SEVERAL = 2
    }
}
