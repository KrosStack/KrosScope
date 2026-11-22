package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.freshness.IndexFreshness

internal object FreshnessSummary {
    private const val AT_ANOTHER = "at another commit"
    private const val NOT_CHECKED = "not checked"

    fun of(freshness: IndexFreshness, repositories: Int): String = when (freshness.state) {
        FreshnessState.BEHIND -> behind(freshness)
        FreshnessState.NOT_BEHIND -> upToDate(freshness, repositories)
        FreshnessState.NOT_CHECKED -> notChecked(freshness)
    }

    private fun behind(freshness: IndexFreshness): String =
        "behind: " + (changes(freshness) + unchecked(freshness, NOT_CHECKED)).joinToString(", ")

    private fun upToDate(freshness: IndexFreshness, repositories: Int): String {
        val unchecked = unchecked(freshness, NOT_CHECKED)
        val count = Format.noun(repositories, "repository", "repositories")
        return if (unchecked.isEmpty()) {
            "up to date ($count)"
        } else {
            "up to date as far as checked ($count; ${unchecked.joinToString(", ")})"
        }
    }

    private fun notChecked(freshness: IndexFreshness): String {
        val parts = unchecked(freshness, "").ifEmpty { listOf("nothing to compare") }.joinToString(", ")
        return "not checked: $parts" + freshness.reason?.let { " ($it)" }.orEmpty()
    }

    private fun changes(freshness: IndexFreshness): List<String> = listOfNotNull(
        freshness.commits.takeIf { it.behind > 0 }?.let(::commitsBehind),
        freshness.workingTrees.takeIf { it.behind > 0 }?.let { treesChanged(it, freshness) },
    )

    private fun unchecked(freshness: IndexFreshness, what: String): List<String> = listOfNotNull(
        freshness.commits.takeIf { it.notChecked > 0 }
            ?.let { share(it.notChecked, it, "commit", "commits", what) },
        freshness.workingTrees.takeIf { it.notChecked > 0 }
            ?.let { share(it.notChecked, it, "working tree", "working trees", what) },
    )

    private fun commitsBehind(counts: FreshnessCounts): String = if (total(counts) == 1) {
        FreshnessText.ANOTHER_COMMIT
    } else {
        share(counts.behind, counts, "repository", "repositories", AT_ANOTHER)
    }

    private fun treesChanged(counts: FreshnessCounts, freshness: IndexFreshness): String {
        val files = Format.noun(freshness.changedFiles, "file") +
            if (freshness.estimate) ", ${FreshnessText.ESTIMATE}" else ""
        return share(counts.behind, counts, "working tree", "working trees", "changed") + " ($files)"
    }

    private fun share(count: Int, counts: FreshnessCounts, singular: String, plural: String, what: String): String {
        val subject = if (total(counts) == 1) singular else "$count of ${total(counts)} $plural"
        return if (what.isEmpty()) subject else "$subject $what"
    }

    private fun total(counts: FreshnessCounts): Int = counts.behind + counts.notBehind + counts.notChecked
}
