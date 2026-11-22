package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.semantic.process.WorkerDiagnostics
import io.github.krosstack.kscope.service.semantic.query.QueryDeadline
import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

public class WorkerEmbeddingProvider internal constructor(
    override val identity: ModelIdentity,
    command: List<String>,
    private val deadlines: WorkerDeadlines = WorkerDeadlines(),
    redact: (String) -> String = { it },
    diagnostics: ((String) -> Unit)? = null,
) : EmbeddingProvider,
    AutoCloseable {
    override val id: String = ID
    private val startupDiagnostics = diagnostics ?: run {
        require(deadlines.absoluteQuery == null) { "Query startup requires a diagnostic sink" }
        System.err::println
    }
    private val process = WorkerProcess(
        command,
        redact,
        routing = WorkerDiagnostics.Routing(
            indexing = if (deadlines.absoluteQuery == null) startupDiagnostics else System.err::println,
            launch = startupDiagnostics,
        ),
        deadline = deadlines.absoluteQuery,
    )
    private val sequence = AtomicInteger()

    internal sealed interface QueryResult {
        class Embedded(val vector: FloatArray) : QueryResult
        data class Refused(val reason: Refusal, val detail: String = "") : QueryResult
    }

    internal enum class Refusal { DEADLINE_EXPIRED, MEMORY_BUSY, STARTUP_FAILED, WORKER_FAILED, WORKER_TIMED_OUT }

    public constructor(component: Path, model: Path = component.resolve("model")) :
        this(ComponentLayout(component.toAbsolutePath(), model.toAbsolutePath()))

    private constructor(layout: ComponentLayout) : this(layout.identity, layout.command())

    init {
        try {
            val request = EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION)
            val ready = deadlines.absoluteQuery?.let {
                process.exchange(request, it.remaining(), it, startupDiagnostics)
            } ?: process.exchange(request, deadlines.startup)
            check(
                ready is EmbeddingResponse.Ready && ready.protocolVersion == EmbeddingProtocol.VERSION &&
                    ready.identity == identity,
            ) { (ready as? EmbeddingResponse.Failed)?.reason ?: "Semantic handshake mismatch" }
        } catch (failure: IllegalStateException) {
            throw IOException(
                process.failureReason("Semantic handshake failed: ${failure.message}", deadlines.absoluteQuery),
                failure,
            )
        } catch (failure: TimeoutException) {
            throw IOException(
                process.failureReason("Semantic handshake deadline exceeded", deadlines.absoluteQuery),
                failure,
            )
        } catch (failure: IOException) {
            runCatching { process.close(deadlines.absoluteQuery) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    override fun embed(texts: List<String>): List<FloatArray> =
        texts.chunked(BATCH_SIZE).flatMap { vectors(it, false, deadlines.batch) }

    override fun embedQuery(text: String): FloatArray = vectors(listOf(text), true, deadlines.query).single()

    internal fun embedQuery(
        text: String,
        deadline: QueryDeadline,
        ownsProcess: Boolean = false,
        diagnostics: (String) -> Unit,
    ): QueryResult {
        if (deadline.expired()) {
            if (ownsProcess) process.close(deadline)
            return QueryResult.Refused(Refusal.DEADLINE_EXPIRED)
        }
        val result = try {
            val id = sequence.incrementAndGet().toString()
            val reply = process.exchange(
                EmbeddingRequest.Embed(id, listOf(text), true),
                deadline.remaining(),
                deadline,
                diagnostics,
                ownsProcess,
            )
            queryResult(reply, id, deadline)
        } catch (failure: TimeoutException) {
            QueryResult.Refused(Refusal.WORKER_TIMED_OUT, failure.message.orEmpty())
        } catch (failure: IOException) {
            QueryResult.Refused(Refusal.WORKER_FAILED, failure.message.orEmpty())
        } catch (failure: IllegalStateException) {
            QueryResult.Refused(Refusal.WORKER_FAILED, failure.message.orEmpty())
        } catch (failure: RejectedExecutionException) {
            QueryResult.Refused(Refusal.WORKER_FAILED, failure.message.orEmpty())
        }
        if (result is QueryResult.Refused && ownsProcess) process.close(deadline)
        return result
    }

    private fun queryResult(reply: EmbeddingResponse, id: String, deadline: QueryDeadline): QueryResult =
        if (reply is EmbeddingResponse.Vectors && reply.requestId == id && valid(reply.vectors, 1)) {
            if (deadline.expired()) {
                QueryResult.Refused(Refusal.DEADLINE_EXPIRED)
            } else {
                QueryResult.Embedded(reply.vectors.single())
            }
        } else {
            val reason = (reply as? EmbeddingResponse.Failed)?.reason ?: "Unexpected semantic response"
            QueryResult.Refused(Refusal.WORKER_FAILED, reason)
        }

    override fun cap(text: String, maxTokens: Int): String {
        val id = sequence.incrementAndGet().toString()
        val reply = process.exchange(EmbeddingRequest.Cap(id, text, maxTokens), deadlines.query)
        if (reply is EmbeddingResponse.Capped && reply.requestId == id) return reply.text
        fail(reply)
    }

    private fun vectors(texts: List<String>, query: Boolean, deadline: Duration): List<FloatArray> {
        val id = sequence.incrementAndGet().toString()
        val reply = process.exchange(EmbeddingRequest.Embed(id, texts, query), deadline)
        if (reply is EmbeddingResponse.Vectors && reply.requestId == id) {
            if (valid(reply.vectors, texts.size)) return reply.vectors
        }
        fail(reply)
    }

    private fun valid(vectors: List<FloatArray>, count: Int): Boolean = vectors.size == count &&
        vectors.all { it.size == identity.dimensions && it.all(Float::isFinite) }

    private fun fail(reply: EmbeddingResponse): Nothing {
        val reason = (reply as? EmbeddingResponse.Failed)?.reason ?: "Unexpected semantic response"
        throw IOException(process.failureReason(reason))
    }

    override fun close(): Unit = process.close()

    internal fun close(deadline: QueryDeadline?): Unit = process.close(deadline)

    internal companion object {
        const val ID = "codeRankEmbed"
        private const val BATCH_SIZE = 16
    }
}
