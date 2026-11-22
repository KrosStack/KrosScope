package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.freshness.IndexFreshness
import java.time.Instant
import java.time.temporal.ChronoUnit

internal object FreshnessText {
    const val ESTIMATE = "estimated from file times"
    const val ANOTHER_COMMIT = "another commit checked out"

    fun line(freshness: IndexFreshness): String? = when (freshness.state) {
        FreshnessState.BEHIND -> behind(freshness)
        FreshnessState.NOT_CHECKED -> freshness.reason?.let { "Index may be behind: $it." }
        FreshnessState.NOT_BEHIND -> null
    }

    fun fields(freshness: IndexFreshness): List<Pair<String, String>> = listOf(
        "Scanned" to (scanned(freshness) ?: "not known"),
        "Commits" to counts(freshness.commits),
        "Working trees" to counts(freshness.workingTrees) + notes(freshness),
    )

    private fun behind(freshness: IndexFreshness): String {
        val scan = scanned(freshness)?.let { " of $it" }.orEmpty()
        val files = files(freshness)
        val pause = if (files != null && freshness.estimate) "," else ""
        val changes = listOfNotNull(commits(freshness.commits), files).joinToString(" and ")
        val doubt = freshness.reason?.let { "; $it" }.orEmpty()
        return "Index is behind: $changes$pause since the scan$scan$doubt."
    }

    private fun scanned(freshness: IndexFreshness): String? =
        freshness.scannedAt?.let { Instant.parse(it).truncatedTo(ChronoUnit.SECONDS).toString() }

    private fun commits(counts: FreshnessCounts): String? = when {
        counts.behind == 0 -> null
        counts.behind + counts.notBehind + counts.notChecked == 1 -> ANOTHER_COMMIT
        else -> Format.noun(counts.behind, "repository", "repositories") + " at another commit"
    }

    private fun files(freshness: IndexFreshness): String? = freshness.changedFiles.takeIf { it > 0 }?.let { count ->
        val bound = if (freshness.reason != null) "at least " else ""
        bound + Format.noun(count, "changed file") + if (freshness.estimate) ", $ESTIMATE" else ""
    }

    private fun counts(counts: FreshnessCounts): String =
        "${counts.behind} behind, ${counts.notBehind} not behind, ${counts.notChecked} not checked"

    private fun notes(freshness: IndexFreshness): String =
        listOfNotNull(files(freshness), freshness.reason).takeIf { it.isNotEmpty() }
            ?.joinToString("; ", " (", ")").orEmpty()
}
