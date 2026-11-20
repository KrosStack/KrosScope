package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider.QueryResult
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider.Refusal
import io.github.krosstack.kscope.service.semantic.query.QueryDeadline
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Starts the worker only when needed, retaining its machine-wide reservation for the owner's lifetime. */
internal class LazyWorkerProvider(
    override val identity: ModelIdentity,
    override val id: String,
    private val start: (QueryDeadline?, ((String) -> Unit)?) -> Start,
) : EmbeddingProvider,
    AutoCloseable {
    sealed interface Start {
        class Refused(val result: QueryResult.Refused) : Start
    }

    class Started(
        val provider: EmbeddingProvider,
        val lost: () -> String?,
        val query: (String, QueryDeadline, Boolean, (String) -> Unit) -> QueryResult,
        val close: (QueryDeadline?) -> Unit,
    ) : Start

    private val lock = ReentrantLock()
    private var started: Started? = null
    private var queryOwned = false
    private var closed = false

    val lost: String? get() = lock.withLock { started?.lost?.invoke() }

    override fun cap(text: String, maxTokens: Int): String = lock.withLock { worker() }.cap(text, maxTokens)

    override fun embed(texts: List<String>): List<FloatArray> = lock.withLock { worker() }.embed(texts)

    override fun embedQuery(text: String): FloatArray = lock.withLock { worker() }.embedQuery(text)

    fun embedQuery(text: String, deadline: QueryDeadline, diagnostics: (String) -> Unit): QueryResult {
        if (deadline.expired()) return QueryResult.Refused(Refusal.DEADLINE_EXPIRED)
        return try {
            if (!lock.tryLock(deadline.remaining().inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
                QueryResult.Refused(Refusal.WORKER_TIMED_OUT)
            } else {
                try {
                    queryLocked(text, deadline, diagnostics)
                } finally {
                    lock.unlock()
                }
            }
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            QueryResult.Refused(Refusal.WORKER_FAILED, failure.message.orEmpty())
        }
    }

    override fun close(): Unit = close(null)

    fun close(deadline: QueryDeadline?) = lock.withLock {
        closed = true
        discard(deadline)
    }

    private fun worker(): EmbeddingProvider {
        check(!closed) { "Semantic worker is closed" }
        queryOwned = false
        val running = started ?: when (val result = start(null, null)) {
            is Started -> result.also { started = it }
            is Start.Refused -> throw IOException(result.result.detail)
        }
        return running.provider
    }

    private fun queryLocked(text: String, deadline: QueryDeadline, diagnostics: (String) -> Unit): QueryResult = when {
        deadline.expired() -> QueryResult.Refused(Refusal.DEADLINE_EXPIRED)

        closed -> QueryResult.Refused(Refusal.WORKER_FAILED, "Semantic worker is closed")

        else -> when (val running = started ?: startQuery(deadline, diagnostics)) {
            is Start.Refused -> running.result

            is Started -> {
                if (started == null) {
                    started = running
                    queryOwned = true
                }
                exchange(running, text, deadline, diagnostics)
            }
        }
    }

    private fun exchange(
        running: Started,
        text: String,
        deadline: QueryDeadline,
        diagnostics: (String) -> Unit,
    ): QueryResult {
        val result = if (deadline.expired()) {
            QueryResult.Refused(Refusal.DEADLINE_EXPIRED)
        } else {
            running.query(text, deadline, queryOwned, diagnostics)
        }
        val timely = if (result is QueryResult.Embedded && deadline.expired()) {
            QueryResult.Refused(Refusal.DEADLINE_EXPIRED)
        } else {
            result
        }
        if (timely is QueryResult.Refused && queryOwned) {
            try {
                discard(deadline)
            } catch (failure: IOException) {
                val cleanup = "cleanup failed: ${failure.message}"
                return timely.copy(detail = if (timely.detail.isEmpty()) cleanup else "${timely.detail}; $cleanup")
            }
        }
        return timely
    }

    private fun startQuery(deadline: QueryDeadline, diagnostics: (String) -> Unit): Start {
        val failure = try {
            return start(deadline, diagnostics)
        } catch (failure: IOException) {
            failure
        } catch (failure: IllegalArgumentException) {
            failure
        } catch (failure: IllegalStateException) {
            failure
        } catch (failure: TimeoutException) {
            failure
        }
        return Start.Refused(
            QueryResult.Refused(
                if (deadline.expired()) Refusal.DEADLINE_EXPIRED else Refusal.STARTUP_FAILED,
                failure.message.orEmpty(),
            ),
        )
    }

    private fun discard(deadline: QueryDeadline? = null) {
        val running = started
        started = null
        queryOwned = false
        running?.close?.invoke(deadline)
    }
}
