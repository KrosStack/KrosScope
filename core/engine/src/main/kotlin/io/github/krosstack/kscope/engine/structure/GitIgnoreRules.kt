package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.discovery.ProcessResult
import java.nio.file.Path

internal sealed interface GitIgnoreRules {
    val notice: String? get() = null

    fun contains(path: Path): Boolean = false

    /** False for a path that the bounded collection of candidates did not reach: Git was not asked about it. */
    fun reached(path: Path): Boolean = true

    class Loaded(private val root: Path, private val ignored: Set<Path>, private val candidates: GitIgnoreCandidates) :
        GitIgnoreRules {
        override fun reached(path: Path): Boolean = candidates.reached(path)

        override fun contains(path: Path): Boolean = generateSequence(path.toAbsolutePath().normalize()) { it.parent }
            .takeWhile { it != root && it.startsWith(root) }.any { it in ignored }
    }

    data object NotWorkTree : GitIgnoreRules

    class Failed(override val notice: String) : GitIgnoreRules

    companion object {
        private const val UNAVAILABLE = "Git ignore rules were not applied: Git is unavailable."
        private const val FAILED = "Git ignore rules were not applied: Git could not check ignored paths."
        private const val NOT_A_REPOSITORY = 128
        private const val NUL = '\u0000'

        fun load(
            root: Path,
            checkoutDirectory: Path?,
            bounds: WalkBounds,
            executable: String,
            runner: GitIgnoreRunner,
        ): GitIgnoreRules {
            val command = listOf(executable, "rev-parse", "--is-inside-work-tree")
            return when (val result = runner.run(command, root, byteArrayOf())) {
                is ProcessResult.Incomplete -> Failed(UNAVAILABLE)

                is ProcessResult.Completed -> when {
                    result.exitCode == 0 && String(result.output, Charsets.UTF_8).trim() == "true" ->
                        listIgnored(root, checkoutDirectory ?: root, bounds, executable, runner)

                    result.exitCode == 0 || result.exitCode == NOT_A_REPOSITORY -> NotWorkTree

                    else -> Failed(FAILED)
                }
            }
        }

        private fun listIgnored(
            root: Path,
            checkoutDirectory: Path,
            bounds: WalkBounds,
            executable: String,
            runner: GitIgnoreRunner,
        ): GitIgnoreRules {
            val candidates = GitIgnoreCandidates.collect(root, bounds)
            val verbose = root != checkoutDirectory
            val command = listOf(executable, "check-ignore", "--no-index", "--stdin", "-z") +
                if (verbose) listOf("--verbose") else emptyList()
            return when (val result = runner.run(command, checkoutDirectory, candidates.input(checkoutDirectory))) {
                is ProcessResult.Incomplete -> Failed(UNAVAILABLE)

                is ProcessResult.Completed -> when (result.exitCode) {
                    0, 1 -> Loaded(
                        root,
                        if (verbose) {
                            GitIgnoreMatches.ignoredBelow(root, checkoutDirectory, result.output)
                        } else {
                            ignoredPaths(checkoutDirectory, result.output)
                        },
                        candidates,
                    )

                    else -> Failed(FAILED)
                }
            }
        }

        private fun ignoredPaths(root: Path, output: ByteArray): Set<Path> =
            String(output, Charsets.UTF_8).split(NUL).filter { it.isNotEmpty() }
                .map { root.resolve(it).normalize() }.toSet()
    }
}
