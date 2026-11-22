package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.engine.structure.GitHeadCommit
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

internal class CheckoutChange(
    val commit: FreshnessState?,
    val tree: FreshnessState?,
    val changedFiles: Int = 0,
    val estimate: Boolean = false,
    val doubt: String? = null,
)

internal class CheckoutQuestion(
    private val checkout: Checkout,
    scan: ScanTimes?,
    private val limitMillis: Long,
    private val deadline: Deadline,
    private val maxFiles: Int = FreshnessCheck.DEFAULT_MAX_FILES,
) {
    private val since: Instant? = scan?.let { Instant.parse(it.startedAt) }

    private val comparison = WorkingTreeComparison(since, scan?.dirtyPaths?.get(checkout.id))

    private val read: String? = checkout.head?.let { GitHeadCommit.of(checkout.root, deadline::expired) }

    val open: Boolean = checkout.tree || (checkout.head != null && read == null)

    fun fromFiles(): CheckoutChange = CheckoutChange(commit(read), null)

    fun late(): CheckoutChange =
        if (checkout.tree) unchecked(read, "this repository could not be checked in $limitMillis ms") else fromFiles()

    fun answer(git: GitChanges): CheckoutChange = when {
        !checkout.tree -> CheckoutChange(commit(git.head(checkout.root)), null)
        checkout.head == null -> estimated()
        else -> compared(git.status(checkout.root))
    }

    private fun commit(now: String?): FreshnessState? = when {
        checkout.head == null -> null
        now == null -> FreshnessState.NOT_CHECKED
        now == checkout.head -> FreshnessState.NOT_BEHIND
        else -> FreshnessState.BEHIND
    }

    private fun tree(changed: Int): FreshnessState =
        if (changed > 0) FreshnessState.BEHIND else FreshnessState.NOT_BEHIND

    private fun unchecked(now: String?, doubt: String): CheckoutChange =
        CheckoutChange(commit(now), FreshnessState.NOT_CHECKED, doubt = doubt)

    private fun compared(status: GitStatus?): CheckoutChange {
        if (status == null) return unchecked(read, FAILED)
        val outcome = comparison.of(status)
        val now = read ?: status.head
        return when {
            outcome.doubt != null && outcome.changed == 0 -> unchecked(now, outcome.doubt)
            else -> CheckoutChange(commit(now), tree(outcome.changed), outcome.changed, doubt = outcome.doubt)
        }
    }

    private fun estimated(): CheckoutChange {
        val time = since ?: return unchecked(null, WorkingTreeComparison.UNDATED)
        val counter = ChangedFileCounter(checkout.root, time, deadline, maxFiles)
        return try {
            Files.walkFileTree(checkout.root, counter)
            when {
                counter.late -> late()
                counter.tooMany -> unchecked(null, "this folder has more than $maxFiles files and was not walked")
                else -> CheckoutChange(null, tree(counter.changed), counter.changed, estimate = true)
            }
        } catch (_: IOException) {
            unchecked(null, FAILED)
        }
    }

    private companion object {
        const val FAILED = "this repository could not be compared with the scan"
    }
}
