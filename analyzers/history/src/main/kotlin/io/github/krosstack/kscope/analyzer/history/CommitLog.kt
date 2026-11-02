package io.github.krosstack.kscope.analyzer.history

/** One commit: the author, by e-mail, the [files] it touched that interest us, and how many files it touched in all. */
internal class GitCommit(val author: String, val files: List<String>, val size: Int = files.size) {
    /** The commit as seen from the directory [prefix] of the work tree, which ends with `/` or is empty. */
    fun within(prefix: String): GitCommit =
        GitCommit(author, files.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }, size)
}

/** The commits of `git log` in the format [ARGUMENTS] asks for, newest first. */
internal object CommitLog {
    const val MAX_COMMITS = 2_000

    val ARGUMENTS = listOf(
        "log",
        "--no-merges",
        "-n",
        MAX_COMMITS.toString(),
        "--no-renames",
        "--name-only",
        "--encoding=UTF-8",
        "--format=%x1e%H%x1f%ae",
    )

    private const val RECORD_SEPARATOR = '\u001E'
    private const val FIELD_SEPARATOR = '\u001F'

    fun parse(output: String): List<GitCommit> = output.split(RECORD_SEPARATOR).drop(1).map(::commitOf)

    private fun commitOf(record: String): GitCommit {
        val lines = record.lines()
        val author = lines.first().substringAfter(FIELD_SEPARATOR)
        return GitCommit(author, lines.drop(1).filter { it.isNotBlank() }.distinct())
    }
}
