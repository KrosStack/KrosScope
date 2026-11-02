package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import java.nio.file.Files
import java.nio.file.Path

/**
 * Adds the repositories that the user registered from elsewhere to the repositories found at the root. A registered
 * repository is left out, with a notice, when its name is already a directory or file of the root, when its path is no
 * longer a work tree, when it has no commit, or when it repeats the id of a repository already in the workspace.
 */
internal class RegisteredCheckouts(
    private val root: Path,
    private val indexDirectory: Path,
    private val probe: (Path) -> GitAccess,
) {
    fun join(local: WorkspaceLayout, registered: List<RegisteredRepository>): WorkspaceLayout {
        val notices = local.notices.toMutableList()
        val unavailable = mutableListOf<RegisteredRepository>()
        val blocked = mutableListOf<BlockingEntry>()
        val names = local.repositories.map { it.directory.value }.toMutableSet()
        val ids = local.repositories.associate { it.repository.id to nameOf(it.directory) }.toMutableMap()
        val joined = registered.sortedBy { it.name }.mapNotNull { entry ->
            when (val candidate = candidateOf(entry, names)) {
                is Candidate.LeftOut -> null.also {
                    notices += candidate.notice
                    if (candidate.awaitsCheckout) unavailable += entry
                    candidate.blocker?.let(blocked::add)
                }

                is Candidate.Kept -> {
                    val id = candidate.repository.repository.id
                    val kept = ids[id]
                    if (kept != null) {
                        val message = "${entry.name} has the same repository id as $kept and is not indexed."
                        notices += notice(DUPLICATE, message)
                    } else {
                        names += entry.name
                        ids[id] = entry.name
                    }
                    candidate.repository.takeIf { kept == null }
                }
            }
        }
        return WorkspaceLayout(
            repositories = (local.repositories + joined).sortedBy { it.directory.value },
            notices = notices,
            candidates = local.candidates,
            withoutCommit = local.withoutCommit,
            registrations = Registrations(registered, unavailable, blocked),
        )
    }

    private fun candidateOf(entry: RegisteredRepository, names: Set<String>): Candidate {
        val workTree = probe(entry.root) as? GitAccess.WorkTree
        val identity = RepositoryIdentity(entry.root, indexDirectory, RepoPath(entry.name))
        val repository = workTree?.let(identity::resolveCommitted)
        return when {
            entry.name in names || Files.exists(root.resolve(entry.name)) -> leftOut(
                NAME_TAKEN,
                "${entry.name} is already a name in the workspace root; it is not indexed.",
                blocker = blockingEntry(entry.name),
            )

            workTree == null -> leftOut(
                MISSING,
                "${entry.name} at ${entry.root} is not a Git work tree and is not indexed.",
                awaitsCheckout = true,
            )

            repository == null ->
                leftOut(WITHOUT_COMMIT, "${entry.name} has no commit yet and is not indexed.", awaitsCheckout = true)

            else -> Candidate.Kept(WorkspaceRepository(entry.root, RepoPath(entry.name), workTree) { repository })
        }
    }

    private fun blockingEntry(name: String): BlockingEntry? =
        root.resolve(name).takeIf(Files::exists)?.let { BlockingEntry(it, Files.isDirectory(it)) }

    private fun leftOut(
        code: String,
        message: String,
        awaitsCheckout: Boolean = false,
        blocker: BlockingEntry? = null,
    ) = Candidate.LeftOut(notice(code, message), awaitsCheckout, blocker)

    private fun nameOf(directory: RepoPath): String = directory.value.ifEmpty { "the workspace root" }

    private fun notice(code: String, message: String) = Diagnostic(
        severity = DiagnosticSeverity.WARNING,
        code = ReasonCode(code),
        message = message,
        location = null,
    )

    private sealed interface Candidate {
        class Kept(val repository: WorkspaceRepository) : Candidate

        class LeftOut(val notice: Diagnostic, val awaitsCheckout: Boolean, val blocker: BlockingEntry?) : Candidate
    }

    private companion object {
        const val DUPLICATE = "workspace.duplicate-repository"
        const val NAME_TAKEN = "workspace.repository-name-taken"
        const val MISSING = "workspace.registered-repository-missing"
        const val WITHOUT_COMMIT = "workspace.repository-without-commit"
    }
}
