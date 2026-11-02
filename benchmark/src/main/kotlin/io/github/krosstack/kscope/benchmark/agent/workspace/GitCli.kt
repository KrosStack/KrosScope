package io.github.krosstack.kscope.benchmark.agent.workspace

import io.github.krosstack.kscope.benchmark.agent.launch.Captured
import io.github.krosstack.kscope.benchmark.agent.launch.ChildEnvironment
import io.github.krosstack.kscope.benchmark.agent.launch.ProcessRunner
import io.github.krosstack.kscope.benchmark.git.RepositorySelection
import java.nio.file.Path

internal class GitCli(
    private val runner: ProcessRunner = ProcessRunner(),
    environment: Map<String, String> = ChildEnvironment.isolatedGit(),
) {
    private val environment: Map<String, String> = RepositorySelection.cleared(environment)

    fun run(directory: Path, vararg arguments: String, input: Path? = null): Captured =
        runner.capture(listOf("git") + CONFIGURATION + arguments, directory, environment, input)

    fun output(directory: Path, vararg arguments: String, input: Path? = null): String {
        val result = run(directory, *arguments, input = input)
        check(result.exitCode == 0) {
            "git ${arguments.firstOrNull()} failed: ${result.stderr.trim().take(ERROR_LIMIT)}"
        }
        return result.stdout
    }

    fun commit(directory: Path, message: String): String = GitCli(runner, ChildEnvironment.isolatedGit(environment))
        .output(directory, "commit", "--no-gpg-sign", "-q", "-m", message)

    fun requireRoot(directory: Path) {
        val root = Path.of(output(directory, "rev-parse", "--show-toplevel").trim()).toRealPath()
        check(root == directory.toRealPath()) { "The directory is not the root of its own Git repository." }
    }

    private companion object {
        const val ERROR_LIMIT = 300
        val CONFIGURATION = listOf(
            "-c", "core.autocrlf=false",
            "-c", "core.safecrlf=false",
            "-c", "core.eol=lf",
            "-c", "core.quotepath=false",
            "-c", "commit.gpgsign=false",
        )
    }
}
