package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.BufferedReader

internal const val EXIT_OK = 0
internal const val EXIT_FAILED = 1

private sealed interface Step {
    data object Continue : Step

    data class Exit(val code: Int) : Step
}

/**
 * Reads requests line by line until `Shutdown` or end of input and answers each of them. A failure that is not part of
 * a request's data ends the loop with a `Failed` response and a non-zero code; the state of the Analysis API cannot be
 * trusted after one.
 */
internal class WorkerLoop(
    private val input: BufferedReader,
    private val writer: ResponseWriter,
    private val service: WorkerService,
) {
    fun run(): Int {
        while (true) {
            val line = input.readLine() ?: return EXIT_OK
            if (line.isBlank()) continue
            val step = handle(line)
            if (step is Step.Exit) return step.code
        }
    }

    private fun handle(line: String): Step {
        val request = runCatching { WorkerProtocol.decodeRequest(line) }.getOrElse { return fail(null, PROTOCOL, it) }
        return when (request) {
            is WorkerRequest.Handshake -> answer(service.ready())
            is WorkerRequest.Analyze -> guarded(request.requestId) { service.analyze(request) }
            is WorkerRequest.Declare -> guarded(request.requestId) { service.declare(request) }
            is WorkerRequest.OutlineScripts -> guarded(request.requestId) { service.outlineScripts(request) }
            is WorkerRequest.DigestApi -> guarded(request.requestId) { service.digestApi(request) }
            WorkerRequest.Shutdown -> Step.Exit(EXIT_OK)
        }
    }

    private fun answer(response: WorkerResponse): Step {
        writer.send(response)
        return Step.Continue
    }

    private fun guarded(requestId: String, action: () -> Unit): Step = runCatching(action).fold(
        onSuccess = { Step.Continue },
        onFailure = { fail(requestId, if (it is OutOfMemoryError) OUT_OF_MEMORY else ANALYSIS_FAILED, it) },
    )

    private fun fail(requestId: String?, reason: String, failure: Throwable): Step {
        failure.printStackTrace()
        val detail = failure.message.orEmpty().lineSequence().firstOrNull().orEmpty()
        val message = "${failure.javaClass.simpleName}: $detail"
        runCatching { writer.send(WorkerResponse.Failed(requestId, ReasonCode(reason), message)) }
        return Step.Exit(EXIT_FAILED)
    }

    private companion object {
        const val PROTOCOL = "worker.protocol"
        const val OUT_OF_MEMORY = "worker.out-of-memory"
        const val ANALYSIS_FAILED = "worker.analysis-failed"
    }
}
