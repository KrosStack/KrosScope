package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.TemporaryTree
import io.github.krosstack.kscope.benchmark.TestGit
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class CorpusRepository(private val id: String, val root: Path = temporaryRoot()) : AutoCloseable {
    private var sequence = 0L

    init {
        git(listOf("init", "-q"))
    }

    fun commit(
        message: String,
        changes: Map<String, String?>,
        author: String = "Human",
        minute: Long = ++sequence,
    ): String {
        changes.forEach { (path, text) ->
            val file = root.resolve(path)
            Files.createDirectories(file.parent)
            if (text == null) Files.delete(file) else Files.writeString(file, text)
        }
        git(listOf("add", "-A"))
        val date = Instant.parse("2024-01-01T00:00:00Z").plusSeconds(minute * 60).toString()
        val identity = mapOf(
            "GIT_AUTHOR_NAME" to author,
            "GIT_AUTHOR_EMAIL" to "$author@example.invalid",
            "GIT_AUTHOR_DATE" to date,
            "GIT_COMMITTER_NAME" to author,
            "GIT_COMMITTER_EMAIL" to "$author@example.invalid",
            "GIT_COMMITTER_DATE" to date,
        )
        git(listOf("commit", "-q", "--allow-empty", "-m", message), identity)
        return git(listOf("rev-parse", "HEAD")).trim()
    }

    fun spec(): RealRepositorySpec = RealRepositorySpec(id, root.toString(), git(listOf("rev-parse", "HEAD")).trim())

    fun git(arguments: List<String>, environment: Map<String, String> = emptyMap()): String {
        val output = Files.createTempFile("kscope-git-output", ".txt")
        try {
            val builder = TestGit.builder(root, arguments, environment).redirectOutput(output.toFile())
            val process = builder.start()
            check(process.waitFor(30, TimeUnit.SECONDS)) { "Synthetic Git command timed out" }
            check(process.exitValue() == 0) { Files.readString(output) }
            return Files.readString(output)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    override fun close() {
        TemporaryTree.delete(root)
    }
}

private fun temporaryRoot(): Path = Files.createTempDirectory("kscope-corpus")
