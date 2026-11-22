package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.semantic.query.CurrentSemanticGeneration
import io.github.krosstack.kscope.service.semantic.query.SemanticQuery
import io.github.krosstack.kscope.service.semantic.query.SemanticQueryOutcome
import io.github.krosstack.kscope.service.semantic.query.SemanticWorkerSession
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.io.IOException
import java.nio.file.Path

/**
 * Owns the semantic indexing job of one workspace. The job advances by one bounded step per [advance], on the thread
 * that owns the workspace, and a scan, an external change or [close] discards it. A failure keeps the generation that
 * was published before.
 */
internal class SemanticRuntime(
    private val directory: Path,
    open: () -> SemanticComponents.Opening,
    private val unavailable: () -> SemanticComponents.Opening.Unavailable?,
    private val workspace: SemanticWorkspace,
    private val now: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private sealed interface Step {
        data class Running(val run: SemanticRun) : Step
        data class Failed(val reason: String, val since: Long) : Step
        data class Complete(val model: ModelIdentity) : Step
        data class Unavailable(val problem: SemanticComponents.Opening.Unavailable, val since: Long) : Step
    }

    private var step: Step? = null
    private var observed = -1L
    private val workers: SemanticWorkerSession = SemanticWorkerSession(open, now) { current.invalidate() }
    private val current = CurrentSemanticGeneration(workspace, directory, componentFingerprint = workers::fingerprint)
    private val query = SemanticQuery(current, workers)

    fun rank(text: String, ml: MlMode = MlMode.ON): SemanticQueryOutcome = if (ml == MlMode.OFF) {
        SemanticQueryOutcome.Skipped(SemanticStatus.OFF, SemanticQueryOutcome.Reason.OFF)
    } else {
        query.rank(text)
    }

    /** Moves the job by one step; true while more can be done at once, false when it is finished or waiting. */
    fun advance(mode: SemanticIndexingMode = SemanticIndexingMode.DEFERRED): Boolean = try {
        workers.expire()
        if (mode == SemanticIndexingMode.OFF) false else advancePending()
    } catch (failure: IOException) {
        finish(Step.Failed(failure.message.orEmpty(), now()))
    } catch (failure: IllegalStateException) {
        finish(Step.Failed(failure.message.orEmpty(), now()))
    } catch (failure: IllegalArgumentException) {
        finish(Step.Failed(failure.message.orEmpty(), now()))
    }

    private fun retryDue(): Boolean = when (val current = step) {
        is Step.Unavailable -> now() - current.since >= RETRY_MILLIS
        is Step.Failed -> now() - current.since >= RETRY_MILLIS
        else -> false
    }

    fun invalidate() = invalidate(null)

    private fun invalidate(next: Step.Failed?) {
        observed = workspace.revision()
        finish(next)
    }

    private fun begin(): Step = when (val opening = workers.acquire()) {
        is SemanticComponents.Opening.Unavailable -> Step.Unavailable(opening, now())

        is SemanticComponents.Opening.Ready -> Step.Running(
            SemanticRun(observed, opening, workspace, directory, now),
        )
    }

    private fun advancePending(): Boolean {
        workspace.refresh()
        if (workspace.revision() != observed) invalidate(step as? Step.Failed)
        if (retryDue()) step = null
        val current = step ?: if (workspace.indexed()) begin().also { step = it } else null
        return (current as? Step.Running)?.let { step(it) } ?: false
    }

    private fun step(current: Step.Running): Boolean {
        if (!workers.owns(current.run.provider)) {
            invalidate()
            return step !is Step.Failed
        }
        return current.run.lostReservation()?.let { finish(Step.Failed(it, now())) } ?: when (current.run.step()) {
            SemanticRun.Outcome.WORKING -> true

            SemanticRun.Outcome.WAITING -> false

            SemanticRun.Outcome.PUBLISHED -> finish(Step.Complete(current.run.provider.identity))

            SemanticRun.Outcome.SUPERSEDED -> {
                invalidate()
                step !is Step.Failed
            }
        }
    }

    private fun finish(next: Step?): Boolean {
        val run = (step as? Step.Running)?.run
        step = next
        val failures = mutableListOf<String>()
        try {
            run?.close()
        } catch (failure: IOException) {
            failures += failure.message.orEmpty()
        }
        try {
            if (next is Step.Complete && failures.isEmpty()) workers.touch() else workers.invalidate()
        } catch (failure: IOException) {
            failures += failure.message.orEmpty()
        }
        if (failures.isNotEmpty()) {
            val reasons = listOfNotNull((next as? Step.Failed)?.reason) + failures
            step = Step.Failed(reasons.joinToString("; cleanup failed: "), now())
        }
        return false
    }

    val status: SemanticStatus
        get() = when (val current = step) {
            null -> unavailable()?.let { SemanticStatus(it.state) }
                ?: SemanticStatus(SemanticState.WORKER_UNAVAILABLE, SERVICE_PROVIDER)

            is Step.Unavailable -> SemanticStatus(current.problem.state)

            is Step.Failed -> SemanticStatus(SemanticState.WORKER_UNAVAILABLE)

            is Step.Complete -> SemanticStatus(SemanticState.USED, SERVICE_PROVIDER, current.model)

            is Step.Running -> current.run.status
        }

    val notice: String?
        get() = when (val current = step) {
            is Step.Failed -> "Semantic indexing failed: ${current.reason}. The previous semantic index stays in use."
            is Step.Unavailable -> current.problem.takeIf { it.state == SemanticState.WORKER_UNAVAILABLE }?.reason
            is Step.Running -> current.run.notice
            else -> null
        }

    /** Steps until the job finishes or waits; a scan in the foreground calls it. */
    fun finish() {
        while (advance()) continue
    }

    override fun close() {
        try {
            invalidate()
        } finally {
            workers.close()
            query.close()
        }
    }

    private companion object {
        const val SERVICE_PROVIDER = WorkerEmbeddingProvider.ID
        const val RETRY_MILLIS = 5 * 60_000L
    }
}
