package io.github.krosstack.kscope.benchmark.agent.workspace

import java.nio.file.Path

internal class Workspace(val root: Path, private val git: GitCli) {
    fun baseFiles(): Set<String> =
        git.output(root, "ls-files", "-z").split('\u0000').filter { it.isNotEmpty() }.toSortedSet()

    fun changedFiles(): List<String> {
        git.requireRoot(root)
        git.output(root, "add", "-A")
        return git.output(root, "diff", "--cached", "--name-only", "-z", "--no-renames", "HEAD")
            .split('\u0000').filter { it.isNotEmpty() && it.substringBefore('/') !in AGENT_STATE_DIRECTORIES }.sorted()
    }

    fun applyPatch(patch: Path): Boolean = git.run(root, "apply", "--whitespace=nowarn", patch.toString()).exitCode == 0

    fun restoreBase() {
        git.requireRoot(root)
        git.output(root, "reset", "-q", "--hard", "HEAD")
        git.output(root, "clean", "-fdq")
    }

    fun copyIn(source: Path) {
        FileTrees.copy(source, root)
    }

    fun forbiddenEntries(forbidden: Set<String>): List<String> = root.toFile().walkTopDown()
        .onEnter { it.name != GIT_DIRECTORY }
        .filter { it.name in forbidden }
        .map { root.relativize(it.toPath()).toString().replace('\\', '/') }
        .sorted()
        .toList()

    private companion object {
        const val GIT_DIRECTORY = ".git"
        val AGENT_STATE_DIRECTORIES = setOf(".claude", ".codex")
    }
}
