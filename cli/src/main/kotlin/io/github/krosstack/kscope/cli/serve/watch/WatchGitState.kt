package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.resolve
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal object WatchGitState {
    private val markers = listOf(
        "index.lock",
        "rebase-merge",
        "rebase-apply",
        "MERGE_HEAD",
        "CHERRY_PICK_HEAD",
        "REVERT_HEAD",
        "BISECT_LOG",
    )
    private const val RESOLVE_TIMEOUT_SECONDS = 5L
    private const val MAX_OUTPUT_BYTES = 4_096

    fun open(workspace: Path, executable: String = "git"): () -> Boolean {
        val directory = runCatching { resolve(workspace, executable) }.getOrNull()
        return fromDirectory(directory)
    }

    fun fromDirectory(directory: Path?): () -> Boolean = fromDirectories { listOfNotNull(directory) }

    fun fromDirectories(directories: () -> List<Path>): () -> Boolean =
        { directories().any { git -> markers.any { Files.exists(git.resolve(it)) } } }

    private fun resolve(workspace: Path, executable: String): Path? {
        val process = ProcessBuilder(executable, "rev-parse", "--is-inside-work-tree", "--absolute-git-dir")
            .directory(workspace.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        return try {
            process.outputStream.close()
            check(process.waitFor(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Git state could not be resolved." }
            readDirectory(process)
        } finally {
            process.destroyForcibly()
            process.inputStream.close()
        }
    }

    private fun readDirectory(process: Process): Path? {
        val lines = process.inputStream.readNBytes(MAX_OUTPUT_BYTES).toString(Charsets.UTF_8).trim().lines()
        if (process.exitValue() != 0 || lines.firstOrNull() != "true") return null
        return lines.getOrNull(1)?.let { Path.of(it).toAbsolutePath().normalize() }
    }
}
