package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.Score

internal fun table(title: String?, header: List<String>, rows: List<List<String>>): String {
    val heading = title?.let { "## $it\n\n" }.orEmpty()
    val head = "| ${header.joinToString(" | ")} |\n|${header.joinToString("|") { "---" }}|"
    val body = rows.joinToString("\n") { "| ${it.joinToString(" | ")} |" }
    return heading + head + (if (body.isEmpty()) "" else "\n$body")
}

internal fun scoreRow(name: String, score: Score) = listOf(
    name,
    format(score.precision),
    format(score.recall),
    format(score.f1),
    score.expected.toString(),
    score.matched.toString(),
)

internal fun countsRow(name: String, score: Score) = listOf(
    name,
    format(score.precision),
    format(score.recall),
    score.expected.toString(),
    score.misses.size.toString(),
    score.extras.size.toString(),
)

/** A titled list of what a score is missing or has too much of, or nothing when the score is exact. */
internal fun listing(title: String, score: Score, unmapped: List<String> = emptyList()): String = listing(
    title,
    score.misses.take(LISTED).map { "- missing: $it" } +
        score.extras.take(LISTED).map { "- extra: $it" } +
        unmapped.take(LISTED).map { "- unmapped: $it" },
)

internal fun listing(title: String, entries: List<String>): String =
    if (entries.isEmpty()) "" else "### $title\n\n" + entries.joinToString("\n")

private const val LISTED = 12
