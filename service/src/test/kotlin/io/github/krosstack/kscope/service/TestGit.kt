package io.github.krosstack.kscope.service

import java.io.IOException
import java.nio.file.Path

internal object TestGit {
    fun run(directory: Path, arguments: List<String>, additions: Map<String, String> = emptyMap()): String {
        val builder = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile()).redirectErrorStream(true)
        builder.environment().putAll(additions)
        val process = builder.start()
        val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
        check(process.waitFor() == 0) { "git $arguments failed: $output" }
        return output
    }

    fun available(): Boolean = try {
        val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        process.waitFor() == 0
    } catch (_: IOException) {
        false
    }
}
