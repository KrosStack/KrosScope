package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealIdentifier
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealWorkspaceMember
import java.nio.file.Files
import java.nio.file.Path

internal object RealRunValidation {
    fun validate(manifest: RealRepositoryManifest, options: RealRunOptions) {
        require(manifest.schemaVersion == 1 && options.identity.isNotBlank()) { "Unsupported or unfrozen run." }
        require(manifest.repositories.map { it.id }.distinct().size == manifest.repositories.size)
        require(manifest.tasks.map { it.id }.distinct().size == manifest.tasks.size)
        val repositories = manifest.repositories.map { it.id }.toSet()
        require(options.repository == null || options.repository in repositories) { "Unknown repository filter." }
        val output = canonicalFuturePath(options.output)
        manifest.repositories.forEach { repository ->
            require(RealIdentifier.isValid(repository.id)) { "Unsafe repository ID." }
            require(validCommit(repository.head)) { "Invalid repository revision." }
            val checkout = canonicalFuturePath(Path.of(repository.checkout))
            require(!output.startsWith(checkout) && !checkout.startsWith(output)) {
                "Run output must be separate from user checkouts."
            }
        }
        manifest.tasks.forEach { task ->
            require(RealIdentifier.isValid(task.id) && task.repository in repositories) { "Invalid task identity." }
            require(validCommit(task.parent) && validCommit(task.commit)) { "Invalid task revision." }
            require(task.level == null || task.level in LEVELS) { "Invalid task level." }
            validateWorkspace(task, repositories)
        }
    }

    private fun validateWorkspace(task: RealCommitTask, repositories: Set<String>) {
        if (task.workspace.isEmpty()) return
        val members = task.workspace.map { it.repository }
        require(members.distinct().size == members.size && repositories.containsAll(members)) {
            "Invalid workspace members."
        }
        require(members.all { DIRECTORY.matches(it) }) { "Workspace member is not a directory name." }
        require(task.workspace.contains(RealWorkspaceMember(task.repository, task.parent, task.commit))) {
            "Workspace does not contain the task repository at its revisions."
        }
        require(task.workspace.all { validCommit(it.parent) && validCommit(it.commit) }) {
            "Invalid workspace revision."
        }
        require(task.goldFiles.all { gold -> members.any { gold.startsWith("$it/") } }) {
            "Gold file outside the workspace members."
        }
    }

    private fun validCommit(commit: String): Boolean = COMMIT.matches(commit)

    private val LEVELS = 1..5
    private val DIRECTORY = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    private val COMMIT = Regex("(?:[0-9a-f]{40}|[0-9a-f]{64})")
}

internal fun canonicalFuturePath(path: Path): Path {
    val absolute = path.toAbsolutePath().normalize()
    var ancestor = absolute
    while (!Files.exists(ancestor) && ancestor.parent != null) ancestor = ancestor.parent
    return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize()
}
