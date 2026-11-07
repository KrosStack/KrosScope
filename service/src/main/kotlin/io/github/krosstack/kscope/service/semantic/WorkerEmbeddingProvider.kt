package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProtocol
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.EmbeddingRequest
import io.github.krosstack.kscope.spi.embedding.EmbeddingResponse
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class WorkerEmbeddingProvider internal constructor(
    override val identity: ModelIdentity,
    command: List<String>,
    private val batchDeadline: Duration = 120.seconds,
    private val queryDeadline: Duration = 10.seconds,
    redact: (String) -> String = { it },
) : EmbeddingProvider,
    AutoCloseable {
    override val id: String = "codeRankEmbed"
    private val process = WorkerProcess(command, redact)
    private val sequence = AtomicInteger()

    public constructor(component: Path, model: Path = component.resolve("model")) :
        this(ComponentLayout(component.toAbsolutePath(), model.toAbsolutePath()))

    private constructor(layout: ComponentLayout) : this(layout.identity, layout.command())

    init {
        try {
            val ready = process.exchange(EmbeddingRequest.Handshake(EmbeddingProtocol.VERSION), batchDeadline)
            check(
                ready is EmbeddingResponse.Ready && ready.protocolVersion == EmbeddingProtocol.VERSION &&
                    ready.identity == identity,
            ) { (ready as? EmbeddingResponse.Failed)?.reason ?: "Semantic handshake mismatch" }
        } catch (failure: IllegalStateException) {
            throw IOException(process.failureReason("Semantic handshake failed: ${failure.message}"), failure)
        }
    }

    override fun embed(texts: List<String>): List<FloatArray> =
        texts.chunked(BATCH_SIZE).flatMap { vectors(it, false, batchDeadline) }

    override fun embedQuery(text: String): FloatArray = vectors(listOf(text), true, queryDeadline).single()

    override fun cap(text: String, maxTokens: Int): String {
        val id = sequence.incrementAndGet().toString()
        val reply = process.exchange(EmbeddingRequest.Cap(id, text, maxTokens), queryDeadline)
        if (reply is EmbeddingResponse.Capped && reply.requestId == id) return reply.text
        fail(reply)
    }

    private fun vectors(texts: List<String>, query: Boolean, deadline: Duration): List<FloatArray> {
        val id = sequence.incrementAndGet().toString()
        val reply = process.exchange(EmbeddingRequest.Embed(id, texts, query), deadline)
        if (reply is EmbeddingResponse.Vectors && reply.requestId == id) {
            val valid = reply.vectors.size == texts.size &&
                reply.vectors.all { it.size == identity.dimensions && it.all(Float::isFinite) }
            if (valid) return reply.vectors
        }
        fail(reply)
    }

    private fun fail(reply: EmbeddingResponse): Nothing {
        val reason = (reply as? EmbeddingResponse.Failed)?.reason ?: "Unexpected semantic response"
        throw IOException(process.failureReason(reason))
    }

    override fun close(): Unit = process.close()

    private companion object {
        const val BATCH_SIZE = 16
    }
}
