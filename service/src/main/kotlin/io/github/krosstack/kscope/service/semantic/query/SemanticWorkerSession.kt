package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.service.semantic.LazyWorkerProvider
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider.QueryResult
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider.Refusal
import java.io.IOException

/** Confined to the workspace owner thread; an indexing borrow lasts until [touch] or [invalidate]. */
internal class SemanticWorkerSession(
    private val open: () -> SemanticComponents.Opening,
    private val now: () -> Long = System::currentTimeMillis,
    private val onInvalidated: () -> Unit = {},
) : AutoCloseable {
    sealed interface Reply {
        class Embedded(val vector: FloatArray) : Reply
        data class Refused(val result: QueryResult.Refused) : Reply
        data class Unavailable(val problem: SemanticComponents.Opening.Unavailable) : Reply
    }

    private var ready: SemanticComponents.Opening.Ready? = null
    private var component = ""
    private var indexing = false
    private var completedAt = now()
    private var closed = false

    fun fingerprint(): String = ready?.fingerprint?.invoke().orEmpty()

    fun owns(provider: LazyWorkerProvider): Boolean = try {
        ready?.provider === provider && fingerprint() == component
    } catch (_: IOException) {
        false
    }

    fun inspect(deadline: QueryDeadline? = null): SemanticComponents.Opening {
        check(!closed) { "Semantic worker session is closed" }
        if (ready != null && fingerprint() != component) invalidate(deadline)
        return ready ?: open().also {
            if (it is SemanticComponents.Opening.Ready) {
                ready = it
                component = it.fingerprint()
            }
        }
    }

    fun acquire(): SemanticComponents.Opening = inspect().also {
        if (it is SemanticComponents.Opening.Ready) indexing = true
    }

    fun query(text: String, deadline: QueryDeadline): Reply {
        val opening = inspect(deadline)
        if (opening is SemanticComponents.Opening.Unavailable) return Reply.Unavailable(opening)
        val provider = (opening as SemanticComponents.Opening.Ready).provider
        return try {
            val result = opening.lost()?.let { QueryResult.Refused(Refusal.MEMORY_BUSY, it) }
                ?: provider.embedQuery(text, deadline) {}
            when (result) {
                is QueryResult.Embedded -> Reply.Embedded(result.vector)

                is QueryResult.Refused -> Reply.Refused(
                    result.copy(detail = result.detail + releaseQuery(deadline)),
                )
            }
        } finally {
            completedAt = now()
        }
    }

    fun touch() {
        indexing = false
        completedAt = now()
    }

    fun releaseQuery(deadline: QueryDeadline? = null): String = try {
        if (!indexing) invalidate(deadline)
        ""
    } catch (failure: IOException) {
        "; cleanup failed: ${failure.message}"
    }

    fun expire(): Boolean {
        if (indexing || (ready?.lost() == null && now() - completedAt < IDLE_MILLIS)) return false
        invalidate()
        return true
    }

    fun invalidate(deadline: QueryDeadline? = null) {
        val previous = ready
        ready = null
        indexing = false
        try {
            previous?.close(deadline)
        } finally {
            if (previous != null) onInvalidated()
        }
    }

    override fun close() {
        closed = true
        invalidate()
    }

    private companion object {
        const val IDLE_MILLIS = 10 * 60_000L
    }
}
