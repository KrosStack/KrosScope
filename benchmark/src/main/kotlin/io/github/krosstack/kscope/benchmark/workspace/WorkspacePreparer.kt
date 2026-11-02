package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.agent.workspace.GitCli
import java.nio.file.Files
import java.nio.file.Path

/**
 * Turns the fixture of a workspace of repositories into one that a scan can open: a copy below a root in which each
 * repository directory is a Git repository with one commit. Author and date of the commit are fixed.
 */
internal class WorkspacePreparer(private val git: GitCli = GitCli()) {
    fun prepare(fixture: Path, root: Path): List<String> {
        copy(fixture, root)
        val repositories = repositoriesIn(root)
        repositories.forEach { commit(root.resolve(it)) }
        return repositories
    }

    private fun copy(fixture: Path, root: Path) {
        Files.walk(fixture).use { paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { file ->
                val target = root.resolve(fixture.relativize(file).toString())
                Files.createDirectories(target.parent)
                Files.copy(file, target)
            }
        }
    }

    private fun repositoriesIn(root: Path): List<String> = Files.list(root).use { paths ->
        paths.filter(Files::isDirectory).map { it.fileName.toString() }.sorted().toList()
    }

    private fun commit(directory: Path) {
        git.output(directory, "init", "-q")
        git.output(directory, "add", "-A")
        git.commit(directory, COMMIT_MESSAGE)
    }

    private companion object {
        const val COMMIT_MESSAGE = "initial"
    }
}
