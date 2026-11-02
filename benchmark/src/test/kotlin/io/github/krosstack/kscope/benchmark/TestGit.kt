package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.agent.workspace.GitCli
import java.nio.file.Path

internal object TestGit {
    fun builder(directory: Path, arguments: List<String>, additions: Map<String, String> = emptyMap()): ProcessBuilder =
        ProcessBuilder(listOf("git") + arguments).directory(directory.toFile()).redirectErrorStream(true).apply {
            environment().putAll(additions)
        }

    fun cli(): GitCli = GitCli(environment = System.getenv())
}
