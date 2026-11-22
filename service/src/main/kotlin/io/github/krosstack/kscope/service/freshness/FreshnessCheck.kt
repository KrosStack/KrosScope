package io.github.krosstack.kscope.service.freshness

import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class Checkout(val root: Path, val head: String?, val tree: Boolean, val id: String = "")

internal class FreshnessCheck(
    private val limitMillis: Long,
    private val executable: String = "git",
    private val maxThreads: Int = DEFAULT_THREADS,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
) {
    fun of(checkouts: List<Checkout>, scan: ScanTimes?): IndexFreshness {
        val deadline = Deadline(limitMillis)
        val questions = checkouts.map { CheckoutQuestion(it, scan, limitMillis, deadline, maxFiles) }
        val answered = ask(questions.filter { it.open }, deadline)
        return summary(questions.map { answered[it] ?: it.fromFiles() }, scan)
    }

    private fun ask(open: List<CheckoutQuestion>, deadline: Deadline): Map<CheckoutQuestion, CheckoutChange> = when {
        open.isEmpty() -> emptyMap()
        deadline.remainingNanos() <= 0 -> open.associateWith { it.late() }
        else -> askGit(open, deadline)
    }

    private fun askGit(open: List<CheckoutQuestion>, deadline: Deadline): Map<CheckoutQuestion, CheckoutChange> {
        val started = StartedProcesses()
        val pool = pool(open.size)
        try {
            val tasks = open.map { Callable { it.answer(GitChanges(executable, started)) } }
            val answers = pool.invokeAll(tasks, deadline.remainingNanos(), TimeUnit.NANOSECONDS)
            return open.zip(answers).associate { (question, answer) ->
                question to if (answer.isCancelled) question.late() else answer.get()
            }
        } finally {
            started.stop()
            pool.shutdownNow()
        }
    }

    private fun pool(questions: Int): ExecutorService =
        Executors.newFixedThreadPool(questions.coerceIn(1, maxThreads)) { task ->
            Thread(task, "kscope-freshness").apply { isDaemon = true }
        }

    private fun summary(changes: List<CheckoutChange>, scan: ScanTimes?): IndexFreshness {
        val commits = counts(changes.mapNotNull { it.commit })
        val trees = counts(changes.map { it.tree ?: FreshnessState.NOT_CHECKED })
        val reason = changes.firstNotNullOfOrNull { it.doubt }
        return IndexFreshness(
            state = state(commits, trees, reason),
            scannedAt = scan?.finishedAt,
            commits = commits,
            workingTrees = trees,
            changedFiles = changes.sumOf { it.changedFiles },
            estimate = changes.any { it.estimate },
            reason = reason,
        )
    }

    private fun counts(states: List<FreshnessState>): FreshnessCounts = FreshnessCounts(
        behind = states.count { it == FreshnessState.BEHIND },
        notBehind = states.count { it == FreshnessState.NOT_BEHIND },
        notChecked = states.count { it == FreshnessState.NOT_CHECKED },
    )

    private fun state(commits: FreshnessCounts, trees: FreshnessCounts, reason: String?): FreshnessState = when {
        commits.behind + trees.behind > 0 -> FreshnessState.BEHIND
        reason != null || commits.notBehind + trees.notBehind == 0 -> FreshnessState.NOT_CHECKED
        else -> FreshnessState.NOT_BEHIND
    }

    companion object {
        const val DEFAULT_THREADS = 8
        const val DEFAULT_MAX_FILES = 200_000
    }
}
