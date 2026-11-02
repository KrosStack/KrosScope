package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.git.RepositorySelection
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class RealSnapshot(
    private val repository: RealRepositorySpec,
    private val storage: RealRunStorage,
    val workspace: Path = storage.scratch(repository.id),
    private val environment: Map<String, String> = System.getenv(),
) {
    fun prepare() {
        if (!Files.exists(workspace)) {
            Files.createDirectories(workspace.parent)
            git(
                workspace.parent,
                listOf(
                    "clone",
                    "--local",
                    "--no-hardlinks",
                    "--",
                    Path.of(repository.checkout).toAbsolutePath().normalize().toString(),
                    workspace.toString(),
                ),
            )
        }
        val checkoutRoot = Path.of(git(workspace, listOf("rev-parse", "--show-toplevel")).trim())
        require(canonicalFuturePath(checkoutRoot) == workspace) {
            "Scratch checkout is not the expected repository root."
        }
        val origin = git(workspace, listOf("remote", "get-url", "origin")).trim()
        require(canonicalFuturePath(Path.of(origin)) == canonicalFuturePath(Path.of(repository.checkout))) {
            "Scratch checkout belongs to another source repository."
        }
        requireClean()
    }

    fun checkout(commit: String) {
        requireClean()
        git(workspace, listOf("checkout", "--detach", "--force", commit, "--"))
        check(git(workspace, listOf("rev-parse", "HEAD")).trim() == commit) { "Checkout revision mismatch." }
    }

    private fun requireClean() {
        val status = git(workspace, listOf("status", "--porcelain", "--untracked-files=all", "--ignored"))
        check(status.isEmpty()) { "Unexpected scratch contents; preserved without scanning or deleting them." }
    }

    private fun git(directory: Path, arguments: List<String>): String {
        val output = storage.gitOutput()
        try {
            val command = listOf(
                "git", "--no-optional-locks", "-c", "core.quotePath=false",
                "-c", "core.autocrlf=false", "-c", "core.eol=lf",
                "-c", "core.hooksPath=${storage.disabledHooks()}",
            ) + arguments
            val builder = ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(output.toFile())
            RepositorySelection.applyTo(builder, environment)
            builder.environment()["GIT_TERMINAL_PROMPT"] = "0"
            builder.environment()["GIT_LFS_SKIP_SMUDGE"] = "1"
            val status = builder.start().waitFor()
            val text = Files.readString(output, Charsets.UTF_8)
            if (status != 0) throw IOException("Git ${arguments.first()} failed ($status): ${text.trim()}")
            return text
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
