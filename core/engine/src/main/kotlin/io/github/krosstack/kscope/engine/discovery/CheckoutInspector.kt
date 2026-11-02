package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.CheckoutInspection
import java.nio.file.Path

/** Finds the top of the work tree that holds a directory, and whether its history has a commit. */
internal class CheckoutInspector(private val executable: String, private val runner: ProcessRunner = ProcessRunner()) {
    fun inspect(directory: Path): CheckoutInspection {
        val start = directory.toAbsolutePath().normalize()
        return when (val git = GitAccess.probe(start, runner, executable)) {
            is GitAccess.Unavailable -> CheckoutInspection.GitUnavailable(git.notice.message)
            GitAccess.NotARepository -> CheckoutInspection.NotARepository
            is GitAccess.WorkTree -> committed(topOf(start) ?: start)
        }
    }

    private fun committed(top: Path): CheckoutInspection {
        val workTree = GitAccess.probe(top, runner, executable) as? GitAccess.WorkTree
        return if (workTree?.rootCommit() != null) {
            CheckoutInspection.Committed(top)
        } else {
            CheckoutInspection.WithoutCommit(top)
        }
    }

    private fun topOf(directory: Path): Path? {
        val result = runner.run(listOf(executable, "rev-parse", "--show-toplevel"), directory)
        if (result !is ProcessResult.Completed || result.exitCode != 0) return null
        val line = String(result.output, Charsets.UTF_8).lineSequence().firstOrNull()?.trim()
        return line?.takeIf { it.isNotEmpty() }?.let { Path.of(it).toAbsolutePath().normalize() }
    }
}
