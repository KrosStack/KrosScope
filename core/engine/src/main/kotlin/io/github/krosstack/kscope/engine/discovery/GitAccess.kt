package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import java.nio.file.Path

internal sealed interface GitFileList {
    class Listed(val paths: List<String>) : GitFileList

    class Failed(val notice: Diagnostic) : GitFileList
}

/** What the engine knows about Git for one workspace root. */
internal sealed interface GitAccess {
    class WorkTree(
        private val runner: ProcessRunner,
        private val root: Path,
        private val executable: String,
        val directory: Path? = null,
    ) : GitAccess {
        /** Tracked files and untracked files that are not ignored, relative to the root and `/`-separated. */
        fun listFiles(): GitFileList {
            val result = git(
                "-c",
                "core.fsmonitor=false",
                "ls-files",
                "-z",
                "--cached",
                "--others",
                "--exclude-standard",
            )
            return when {
                result is ProcessResult.Incomplete -> failed(result.reason)

                result is ProcessResult.Completed && result.exitCode == 0 ->
                    GitFileList.Listed(nulSeparated(result.output))

                else -> failed("git ls-files failed")
            }
        }

        /** The smallest root commit hash, or null when the history has no commit yet. */
        fun rootCommit(): String? = lines(git("rev-list", "--max-parents=0", "HEAD")).minOrNull()

        fun headRevision(): String? = lines(git("rev-parse", "HEAD")).firstOrNull()

        /** Differs after every move of HEAD; read from the files of Git when [directory] is known, else from Git. */
        fun headToken(): String = directory?.let { GitHeadFiles(it).token() } ?: headRevision().orEmpty()

        private fun git(vararg arguments: String) = runner.run(listOf(executable) + arguments, root)

        private fun lines(result: ProcessResult): List<String> {
            if (result !is ProcessResult.Completed || result.exitCode != 0) return emptyList()
            return String(result.output, Charsets.UTF_8).lines().filter { it.isNotBlank() }
        }

        private fun nulSeparated(output: ByteArray): List<String> =
            String(output, Charsets.UTF_8).split(NUL).filter { it.isNotEmpty() }

        private fun failed(reason: String) = GitFileList.Failed(gitNotice(reason))
    }

    data object NotARepository : GitAccess

    class Unavailable(val notice: Diagnostic) : GitAccess

    companion object {
        const val DEFAULT_EXECUTABLE = "git"
        private const val NUL = '\u0000'

        fun probe(
            root: Path,
            runner: ProcessRunner = ProcessRunner(),
            executable: String = DEFAULT_EXECUTABLE,
            resolveDirectory: Boolean = false,
        ): GitAccess {
            val command = listOf(executable, "rev-parse", "--is-inside-work-tree") +
                if (resolveDirectory) listOf("--absolute-git-dir") else emptyList()
            return when (val result = runner.run(command, root)) {
                is ProcessResult.Incomplete -> Unavailable(gitNotice(result.reason))

                is ProcessResult.Completed -> if (result.isInsideWorkTree()) {
                    val directory = String(result.output, Charsets.UTF_8).lines().getOrNull(1)
                        ?.takeIf { it.isNotBlank() }?.let { Path.of(it).toAbsolutePath().normalize() }
                    WorkTree(runner, root, executable, directory)
                } else {
                    NotARepository
                }
            }
        }

        private fun ProcessResult.Completed.isInsideWorkTree() =
            exitCode == 0 && String(output, Charsets.UTF_8).lineSequence().firstOrNull()?.trim() == "true"
    }
}

private fun gitNotice(reason: String) = Diagnostic(
    severity = DiagnosticSeverity.INFO,
    code = ReasonCode("discovery.git-unavailable"),
    message = "Falling back to a directory walk: $reason",
    location = null,
)
