package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** What the answers of a worker mean for the request or the handshake they belong to. */
internal object WorkerReplies {
    /** The outcome a response brings to request [id], or null when the request goes on. */
    fun conclusionOf(response: WorkerResponse, id: String, onResponse: (WorkerResponse) -> Unit): WorkerOutcome? =
        when {
            response is WorkerResponse.Failed -> WorkerOutcome.Failed("${response.reason.value}: ${response.message}")
            requestIdOf(response) != id -> null
            response is WorkerResponse.Completed -> WorkerOutcome.Completed
            else -> deliver(response, onResponse)
        }

    private fun deliver(response: WorkerResponse, onResponse: (WorkerResponse) -> Unit): WorkerOutcome? {
        onResponse(response)
        return null
    }

    /** Why the first answer of a worker rules it out, or null when it is a ready worker of this protocol. */
    fun handshakeProblem(answer: Answer, timeout: Duration): String? = when (answer) {
        is Answer.Message -> problemWith(answer.response)
        is Answer.Unreadable -> "it answered the handshake with a message that cannot be read"
        Answer.Closed -> "it exited before it answered the handshake"
        Answer.TimedOut -> "it did not answer the handshake within $timeout"
        Answer.Interrupted -> "the handshake was interrupted"
    }

    private fun problemWith(response: WorkerResponse): String? = when {
        response !is WorkerResponse.Ready -> "it answered the handshake unexpectedly"

        response.protocolVersion != WorkerProtocol.PROTOCOL_VERSION ->
            "it speaks protocol ${response.protocolVersion}, this client ${WorkerProtocol.PROTOCOL_VERSION}"

        else -> null
    }

    private fun requestIdOf(response: WorkerResponse): String? = when (response) {
        is WorkerResponse.Ready -> null
        is WorkerResponse.Batch -> response.requestId
        is WorkerResponse.Outline -> response.requestId
        is WorkerResponse.ApiDigest -> response.requestId
        is WorkerResponse.Completed -> response.requestId
        is WorkerResponse.Failed -> response.requestId
    }
}

internal fun Process.exitsWithin(timeout: Duration): Boolean = try {
    waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
} catch (_: InterruptedException) {
    Thread.currentThread().interrupt()
    false
}

/** Asks the process to end and forces it to if it does not within [grace]. */
internal fun Process.terminate(grace: Duration) {
    destroy()
    if (!exitsWithin(grace)) destroyForcibly()
}
