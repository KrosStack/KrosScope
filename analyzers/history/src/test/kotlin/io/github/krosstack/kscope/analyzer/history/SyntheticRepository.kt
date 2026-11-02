package io.github.krosstack.kscope.analyzer.history

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.relativeTo

/** A Git repository in a temporary directory whose commits have fixed authors and dates, so that runs are alike. */
internal class SyntheticRepository(
    val directory: Path = Files.createTempDirectory("kscope-history"),
    initialize: Boolean = true,
) : AutoCloseable {
    private var minutes = 0L

    init {
        assumeGit()
        if (initialize) git("init", "-q")
    }

    fun commit(author: String, vararg changes: Pair<String, String?>) = commit(author, changes.toList())

    /** Applies [changes], a path with its new content or null to delete it, and commits everything. */
    fun commit(author: String, changes: List<Pair<String, String?>>) {
        changes.forEach { (path, content) -> if (content == null) delete(path) else write(path, content) }
        git("add", "-A")
        val date = Instant.parse("2024-01-01T00:00:00Z").plusSeconds(++minutes * SECONDS_PER_MINUTE).toString()
        val message = listOf("commit", "-q", "--allow-empty", "-m", "commit $minutes")
        run(message, identity(author, date))
    }

    fun write(path: String, content: String) {
        val target = directory.resolve(path)
        Files.createDirectories(target.parent)
        Files.writeString(target, content)
    }

    fun delete(path: String) {
        Files.delete(directory.resolve(path))
    }

    fun head(): String = git("rev-parse", "HEAD")

    /** The files below [workspace], as paths relative to it, without the Git directory. */
    fun files(workspace: Path = directory): Set<String> = Files.walk(workspace).use { paths ->
        paths.filter { Files.isRegularFile(it) && ".git" !in it.relativeTo(directory).map(Path::toString) }
            .map { it.relativeTo(workspace).joinToString("/") }
            .toList()
            .toSet()
    }

    fun git(vararg arguments: String): String = run(arguments.toList(), emptyMap())

    private fun run(arguments: List<String>, environment: Map<String, String>): String =
        TestGit.run(directory, arguments, environment)

    private fun identity(author: String, date: String) = mapOf(
        "GIT_AUTHOR_NAME" to author,
        "GIT_AUTHOR_EMAIL" to "$author@example.invalid",
        "GIT_AUTHOR_DATE" to date,
        "GIT_COMMITTER_NAME" to author,
        "GIT_COMMITTER_EMAIL" to "$author@example.invalid",
        "GIT_COMMITTER_DATE" to date,
    )

    override fun close() {
        TemporaryTree.delete(directory)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}

/** Skips the test on a machine without Git. */
internal fun assumeGit() {
    assumeTrue(TestGit.available(), "Git is not available")
}
