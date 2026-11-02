package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import kotlinx.serialization.SerializationException
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.time.Duration

/** What became of a request that was written to a worker. */
internal enum class Delivery {
    SENT,

    /** The worker has gone: its input is closed. */
    BROKEN,

    /** The worker is alive but does not read, so that the write did not end in time. */
    STALLED,
}

internal sealed interface Answer {
    class Message(val response: WorkerResponse) : Answer

    class Unreadable(val line: String) : Answer

    data object Closed : Answer

    data object TimedOut : Answer

    data object Interrupted : Answer
}

/**
 * The standard streams of a worker as protocol messages. Two daemon threads read its output, so a worker is never
 * blocked on a full pipe; lines on standard output that are not protocol messages are treated as diagnostics. A line
 * longer than [maxLine] characters is not kept: on standard output it is a message that cannot be read. Requests are
 * written on a daemon thread of their own, so that a worker that stops reading its input, with the pipe full, costs a
 * timeout and not the thread that sent the request.
 */
internal class WorkerChannel(
    process: Process,
    private val onDiagnostic: (String) -> Unit,
    private val maxLine: Int = MAX_LINE,
) {
    private val answers = LinkedBlockingQueue<Answer>()
    private val recent = RecentLines(RECENT_LINES)
    private val requests = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kscope-kotlin-worker-stdin").apply { isDaemon = true }
    }

    init {
        pump("stdout", process.inputStream, ::acceptOutput, ::rejectOverlongOutput) { answers.put(Answer.Closed) }
        pump("stderr", process.errorStream, ::acceptDiagnostic, ::acceptDiagnostic) {}
    }

    /** Writes [request] and waits at most [timeout] for the worker to take all of it. */
    fun send(request: WorkerRequest, timeout: Duration): Delivery {
        val line = WorkerProtocol.encode(request) + "\n"
        val write = try {
            writer.submit<Delivery> { write(line) }
        } catch (_: RejectedExecutionException) {
            return Delivery.BROKEN
        }
        return try {
            write.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            Delivery.STALLED
        } catch (_: ExecutionException) {
            Delivery.BROKEN
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Delivery.BROKEN
        }
    }

    fun next(timeout: Duration): Answer = try {
        answers.poll(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) ?: Answer.TimedOut
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        Answer.Interrupted
    }

    /** Closes the input after the request in flight, if there is one; it does not wait for a stalled worker. */
    fun closeInput() {
        runCatching { writer.execute { runCatching { requests.close() } } }
        writer.shutdown()
    }

    /** The last lines the worker wrote as diagnostics, as the end of a failure message; empty when there are none. */
    fun recentOutputSuffix(): String = recent.text().takeIf { it.isNotEmpty() }?.let { " (last output: $it)" }.orEmpty()

    private fun write(line: String): Delivery = try {
        requests.write(line)
        requests.flush()
        Delivery.SENT
    } catch (_: IOException) {
        Delivery.BROKEN
    }

    private fun acceptOutput(line: String) {
        if (line.startsWith(MESSAGE_START)) answers.put(decode(line)) else acceptDiagnostic(line)
    }

    private fun decode(line: String): Answer = try {
        Answer.Message(WorkerProtocol.decodeResponse(line))
    } catch (_: SerializationException) {
        Answer.Unreadable(line.take(UNREADABLE_PREVIEW))
    }

    private fun rejectOverlongOutput(start: String) {
        answers.put(Answer.Unreadable(start.take(UNREADABLE_PREVIEW)))
    }

    private fun acceptDiagnostic(line: String) {
        recent.add(line)
        onDiagnostic(line)
    }

    private fun pump(
        name: String,
        stream: InputStream,
        onLine: (String) -> Unit,
        onOverlong: (String) -> Unit,
        onEnd: () -> Unit,
    ) {
        thread(isDaemon = true, name = "kscope-kotlin-worker-$name") {
            runCatching { BoundedLines(stream.reader(Charsets.UTF_8), maxLine).forEachLine(onLine, onOverlong) }
            onEnd()
        }
    }

    companion object {
        /** A message holds a few hundred facts, far below this; the limit stops a runaway line filling the heap. */
        const val MAX_LINE = 64 * 1024 * 1024
        private const val MESSAGE_START = "{"
        private const val RECENT_LINES = 5
        private const val UNREADABLE_PREVIEW = 200
    }
}

private class RecentLines(private val capacity: Int) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun text(): String = lines.joinToString(" | ")
}
