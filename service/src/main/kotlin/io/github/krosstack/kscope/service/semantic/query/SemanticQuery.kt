package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.semantic.search.SemanticFileRanking
import io.github.krosstack.kscope.semantic.store.GenerationOpening
import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider.Refusal
import io.github.krosstack.kscope.service.semantic.query.SemanticQueryOutcome.Reason
import java.io.IOException

internal class SemanticQuery(
    private val current: CurrentSemanticGeneration,
    private val session: SemanticWorkerSession,
    private val deadline: () -> QueryDeadline = ::QueryDeadline,
) : AutoCloseable {
    fun rank(text: String): SemanticQueryOutcome {
        val deadline = deadline()
        return try {
            rank(text, deadline)
        } catch (failure: IOException) {
            skipped(Reason.STARTUP_FAILED, deadline, failure.message.orEmpty())
        } catch (failure: IllegalArgumentException) {
            skipped(Reason.STARTUP_FAILED, deadline, failure.message.orEmpty())
        } catch (failure: IllegalStateException) {
            skipped(Reason.STARTUP_FAILED, deadline, failure.message.orEmpty())
        }
    }

    private fun rank(text: String, deadline: QueryDeadline): SemanticQueryOutcome {
        if (deadline.expired()) return skipped(Reason.DEADLINE_EXPIRED, deadline)
        val opening = session.inspect(deadline)
        return when (opening) {
            is SemanticComponents.Opening.Unavailable -> unavailable(opening, deadline)
            is SemanticComponents.Opening.Ready -> admit(text, deadline, opening.provider)
        }
    }

    private fun admit(text: String, deadline: QueryDeadline, provider: LazyWorkerProvider): SemanticQueryOutcome =
        when (val admitted = current.open(provider)) {
            is CurrentSemanticGeneration.Opening.Ready -> embed(text, deadline, provider, admitted.snapshot)

            is CurrentSemanticGeneration.Opening.Unproven -> skipped(
                Reason.UNPROVEN_INVENTORY,
                deadline,
                admitted.reason,
            )

            is CurrentSemanticGeneration.Opening.Unavailable -> skipped(
                when (admitted.opening) {
                    GenerationOpening.Busy -> Reason.ADMISSION_BUSY
                    else -> Reason.UNAVAILABLE_GENERATION
                },
                deadline,
            )
        }

    private fun embed(
        text: String,
        deadline: QueryDeadline,
        provider: LazyWorkerProvider,
        snapshot: CurrentSemanticGeneration.Snapshot,
    ): SemanticQueryOutcome {
        if (deadline.expired()) return skipped(Reason.DEADLINE_EXPIRED, deadline)
        return when (val reply = session.query(text, deadline)) {
            is SemanticWorkerSession.Reply.Embedded -> ranked(snapshot, provider, reply.vector, deadline)

            is SemanticWorkerSession.Reply.Refused -> skipped(
                reason(reply.result.reason),
                deadline,
                reply.result.detail,
            )

            is SemanticWorkerSession.Reply.Unavailable -> unavailable(reply.problem, deadline)
        }
    }

    private fun ranked(
        snapshot: CurrentSemanticGeneration.Snapshot,
        provider: LazyWorkerProvider,
        vector: FloatArray,
        deadline: QueryDeadline,
    ): SemanticQueryOutcome {
        val files = SemanticFileRanking.rank(snapshot.entries, vector)
        val checked = current.open(provider)
        val state = if (snapshot.behindChunks == 0) SemanticState.USED else SemanticState.BEHIND
        return when {
            !session.owns(provider) || checked !is CurrentSemanticGeneration.Opening.Ready ||
                checked.snapshot.identity != snapshot.identity ->
                skipped(Reason.SUPERSEDED, deadline)

            deadline.expired() -> skipped(Reason.DEADLINE_EXPIRED, deadline)

            else -> SemanticQueryOutcome.Applied(
                files,
                SemanticStatus(state, provider.id, provider.identity, snapshot.behindChunks),
                snapshot.identity,
                snapshot.behindChunks,
            )
        }
    }

    private fun reason(refusal: Refusal): Reason = when (refusal) {
        Refusal.DEADLINE_EXPIRED -> Reason.DEADLINE_EXPIRED
        Refusal.MEMORY_BUSY -> Reason.MEMORY_BUSY
        Refusal.STARTUP_FAILED -> Reason.STARTUP_FAILED
        Refusal.WORKER_FAILED -> Reason.WORKER_FAILED
        Refusal.WORKER_TIMED_OUT -> Reason.WORKER_TIMED_OUT
    }

    private fun unavailable(
        problem: SemanticComponents.Opening.Unavailable,
        deadline: QueryDeadline,
    ): SemanticQueryOutcome.Skipped = when (problem.state) {
        SemanticState.NOT_INSTALLED -> skipped(Reason.NOT_INSTALLED, deadline, problem.reason, problem.state)
        SemanticState.DISABLED -> skipped(Reason.DISABLED, deadline, problem.reason, problem.state)
        else -> skipped(Reason.STARTUP_FAILED, deadline, problem.reason)
    }

    private fun skipped(
        reason: Reason,
        deadline: QueryDeadline,
        detail: String = "",
        state: SemanticState = SemanticState.WORKER_UNAVAILABLE,
    ): SemanticQueryOutcome.Skipped {
        invalidate()
        val cleanup = session.releaseQuery(deadline)
        return SemanticQueryOutcome.Skipped(SemanticStatus(state), reason, detail + cleanup)
    }

    fun invalidate() = current.invalidate()

    override fun close() = current.close()
}
