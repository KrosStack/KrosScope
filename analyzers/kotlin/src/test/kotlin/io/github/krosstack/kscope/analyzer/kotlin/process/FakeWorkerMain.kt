package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.worker.ScriptCall
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * A worker for tests that speaks the protocol and misbehaves on demand. The first argument is the mode, the second
 * the file that receives the process id.
 */
object FakeWorkerMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.first()
        Files.writeString(Path.of(args[1]), ProcessHandle.current().pid().toString())
        when (mode) {
            "silent" -> blockForever()
            "crash-start" -> crashAtStart()
            else -> serve(mode)
        }
    }

    private fun crashAtStart(): Nothing {
        System.err.println("boom: the worker cannot start")
        exitProcess(CRASH_CODE)
    }

    private fun serve(mode: String) {
        if (mode == "noise") println("a line that is not a protocol message")
        while (true) {
            when (val request = WorkerProtocol.decodeRequest(readlnOrNull() ?: exitProcess(0))) {
                is WorkerRequest.Handshake -> answerHandshake(mode)
                is WorkerRequest.Analyze -> analyse(mode, request.requestId, request.modules)
                is WorkerRequest.Declare -> analyse(mode, request.requestId, request.modules)
                is WorkerRequest.OutlineScripts -> outline(mode, request)
                is WorkerRequest.DigestApi -> FakeApiDigests.answer(mode, request)
                WorkerRequest.Shutdown -> exitProcess(0)
            }
        }
    }

    private fun answerHandshake(mode: String) {
        val version = when (mode) {
            "old-protocol" -> WorkerProtocol.PROTOCOL_VERSION + 1
            "protocol-three" -> OLD_VERSION
            else -> WorkerProtocol.PROTOCOL_VERSION
        }
        send(WorkerResponse.Ready(version, FakeBatches.producer, setOf(AnalyzerCapability.DECLARATIONS)))
        if (mode == "deaf") blockForever()
    }

    private fun analyse(mode: String, requestId: String, modules: List<WorkerModule>) {
        if (mode == "stderr") System.err.println("fake diagnostic line")
        val batches = modules.flatMap { FakeBatches.of(mode, requestId, it) }
        if (mode == "garbage") return garbage()
        if (mode in INTERRUPTING_MODES) interruptedAfterFirstBatch(mode, requestId, batches)
        when (mode) {
            "parts-gap" -> batches.filterIndexed { index, _ -> index != 1 }.forEach(::send)
            "parts-unfinished" -> batches.take(2).forEach(::send)
            "parts-exit" -> interruptedAfterFirstBatch("exit", requestId, batches)
            else -> batches.forEach(::send)
        }
        send(WorkerResponse.Completed(requestId))
    }

    private fun garbage() {
        println("{this is not a response")
        System.out.flush()
    }

    private fun interruptedAfterFirstBatch(mode: String, requestId: String, batches: List<WorkerResponse>): Nothing {
        batches.take(1).forEach(::send)
        if (mode == "hang") blockForever()
        if (mode == "exit") exitProcess(EXIT_CODE)
        send(WorkerResponse.Failed(requestId, ReasonCode("worker.analysis-failed"), "kaput"))
        exitProcess(1)
    }

    private fun outline(mode: String, request: WorkerRequest.OutlineScripts) {
        if (mode == "exit") exitProcess(EXIT_CODE)
        request.scripts.forEach { script ->
            val call = ScriptCall("plugins", null, emptyList(), emptyList(), emptyMap(), TextRange(1, 1, 1, 1))
            send(WorkerResponse.Outline(request.requestId, script.ref, ScriptOutline(listOf(call))))
        }
        send(WorkerResponse.Completed(request.requestId))
    }

    private fun send(response: WorkerResponse) {
        println(WorkerProtocol.encode(response))
        System.out.flush()
    }

    private fun blockForever(): Nothing {
        Thread.currentThread().join()
        error("Unreachable")
    }

    private val INTERRUPTING_MODES = setOf("fail", "hang", "exit")
    private const val CRASH_CODE = 3
    private const val EXIT_CODE = 7
    private const val OLD_VERSION = 3
}
