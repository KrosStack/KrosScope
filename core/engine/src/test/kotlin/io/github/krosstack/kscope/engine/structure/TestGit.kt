package io.github.krosstack.kscope.engine.structure

import java.io.IOException
import java.nio.file.Path

internal object TestGit {
    /**
     * On a loaded machine git was seen to fail without a word once. A failure with an empty output is run again, a
     * few times; one with a message fails at once.
     */
    fun output(directory: Path, vararg arguments: String): String {
        var attempt = 1
        while (true) {
            val result = run(directory, arguments)
            if (result.exitCode == 0) return result.text
            check(result.text.isEmpty() && attempt < ATTEMPTS) {
                "git ${arguments.toList()} failed with exit code ${result.exitCode}: ${result.text}"
            }
            attempt++
        }
    }

    private class GitResult(val exitCode: Int, val text: String)

    private fun run(directory: Path, arguments: Array<out String>): GitResult {
        val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile())
            .redirectErrorStream(true).start()
        val text = process.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
        return GitResult(process.waitFor(), text)
    }

    private const val ATTEMPTS = 3

    fun available(): Boolean = try {
        val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        process.waitFor() == 0
    } catch (_: IOException) {
        false
    }
}
