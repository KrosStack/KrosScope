package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.worker.BatchParts
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * A running worker JVM that answers one request at a time. Closing it ends the process: through a shutdown request
 * when the worker still listens, and through [Process.destroy] when it does not exit in time. A shutdown hook also
 * ends it if the client JVM stops first.
 */
internal class KotlinWorkerProcess private constructor(
    private val process: Process,
    private val channel: WorkerChannel,
    private val timeouts: WorkerTimeouts,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val requestNumbers = AtomicInteger()
    private val cleanup = Thread({ process.destroyForcibly() }, "kscope-kotlin-worker-cleanup")

    val isRunning: Boolean get() = process.isAlive

    init {
        runCatching { Runtime.getRuntime().addShutdownHook(cleanup) }
    }

    /** Sends an `analyze` request, or a `declare` request when [declarationsOnly] holds, and passes on its batches. */
    fun analyze(
        modules: List<WorkerModule>,
        declarationsOnly: Boolean = false,
        onBatch: (FactBatch) -> Unit,
    ): WorkerOutcome {
        val id = nextRequestId(if (declarationsOnly) "declare" else "analyze")
        val request = if (declarationsOnly) WorkerRequest.Declare(id, modules) else WorkerRequest.Analyze(id, modules)
        val assembler = BatchParts.Assembler()
        return try {
            val outcome = exchange(request, id) { response ->
                if (response is WorkerResponse.Batch) assembler.accept(response)?.let(onBatch)
            }
            val unfinished = assembler.incomplete()
            if (outcome is WorkerOutcome.Completed && unfinished != null) {
                abort("The worker completed the request in the middle of the batch of ${unfinished.key}")
            } else {
                outcome
            }
        } catch (violation: BatchParts.OutOfSequence) {
            abort("The worker broke the order of its messages: ${violation.message}")
        }
    }

    fun outline(scripts: List<WorkerFile>, onOutline: (ArtifactRef, ScriptOutline) -> Unit): WorkerOutcome {
        val id = nextRequestId("outline")
        return exchange(WorkerRequest.OutlineScripts(id, scripts), id) { response ->
            if (response is WorkerResponse.Outline) onOutline(response.ref, response.outline)
        }
    }

    fun digestApi(sources: List<WorkerFile>, onDigest: (ArtifactRef, String?) -> Unit): WorkerOutcome {
        val id = nextRequestId("digest-api")
        return exchange(WorkerRequest.DigestApi(id, sources), id) { response ->
            if (response is WorkerResponse.ApiDigest) onDigest(response.ref, response.digest)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        channel.send(WorkerRequest.Shutdown, timeouts.shutdown)
        channel.closeInput()
        if (!process.exitsWithin(timeouts.shutdown)) process.terminate(DESTROY_GRACE)
        runCatching { Runtime.getRuntime().removeShutdownHook(cleanup) }
    }

    private fun nextRequestId(kind: String) = "$kind-${requestNumbers.incrementAndGet()}"

    private fun exchange(request: WorkerRequest, id: String, onResponse: (WorkerResponse) -> Unit): WorkerOutcome =
        when (channel.send(request, timeouts.response)) {
            Delivery.SENT -> awaitConclusion(id, onResponse)
            Delivery.BROKEN -> WorkerOutcome.Failed("The worker does not read its requests")
            Delivery.STALLED -> abort("The worker did not take its request within ${timeouts.response}")
        }

    private fun awaitConclusion(id: String, onResponse: (WorkerResponse) -> Unit): WorkerOutcome {
        while (true) {
            val outcome = outcomeOf(channel.next(timeouts.response), id, onResponse)
            if (outcome != null) return outcome
        }
    }

    private fun outcomeOf(answer: Answer, id: String, onResponse: (WorkerResponse) -> Unit): WorkerOutcome? =
        when (answer) {
            is Answer.Message -> WorkerReplies.conclusionOf(answer.response, id, onResponse)
            is Answer.Unreadable -> abort("The worker sent a message that cannot be read: ${answer.line}")
            Answer.Closed -> WorkerOutcome.Failed(exitDescription())
            Answer.TimedOut -> abort("The worker did not answer within ${timeouts.response}")
            Answer.Interrupted -> abort("The request was interrupted")
        }

    private fun abort(message: String): WorkerOutcome.Failed {
        process.destroy()
        return WorkerOutcome.Failed(message)
    }

    private fun exitDescription(): String {
        val code = if (process.exitsWithin(EXIT_CODE_WAIT)) " with exit code ${process.exitValue()}" else ""
        return "The worker exited$code${channel.recentOutputSuffix()}"
    }

    private fun handshake(): WorkerStart {
        val sent = channel.send(WorkerRequest.Handshake(WorkerProtocol.PROTOCOL_VERSION), timeouts.handshake)
        val problem = if (sent == Delivery.SENT) {
            WorkerReplies.handshakeProblem(channel.next(timeouts.handshake), timeouts.handshake)
        } else {
            "it does not read its input"
        }
        if (problem == null) return WorkerStart.Started(this)
        val reason = "$problem${channel.recentOutputSuffix()}"
        process.destroy()
        close()
        return WorkerStart.Unavailable(reason)
    }

    companion object {
        private val EXIT_CODE_WAIT = 2.seconds
        private val DESTROY_GRACE = 5.seconds

        fun start(command: List<String>, timeouts: WorkerTimeouts, onDiagnostic: (String) -> Unit): WorkerStart {
            val process = try {
                ProcessBuilder(command).start()
            } catch (failure: IOException) {
                return WorkerStart.Unavailable("the process could not be started (${failure.javaClass.simpleName})")
            }
            return KotlinWorkerProcess(process, WorkerChannel(process, onDiagnostic), timeouts).handshake()
        }
    }
}
