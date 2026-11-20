package io.github.krosstack.kscope.service.semantic.process

import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal class WorkerDiagnostics(
    private val process: Process,
    private val routing: Routing,
    private val retain: (String) -> Unit,
    private val closed: () -> Boolean,
    query: Boolean,
) {
    class Routing(
        val indexing: (String) -> Unit = System.err::println,
        val launch: (String) -> Unit = indexing,
        val read: (BufferedReader, CharArray) -> Int = { stream, buffer -> stream.read(buffer, 0, buffer.size) },
    )

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val reader = process.errorStream.bufferedReader(Charsets.UTF_8)
    private var sink = routing.launch
    private var previousQuery = query
    private var pending = 0
    private val tail = StringBuilder()
    private var tailSink = sink
    private var discarding = false
    private val forwarded = ArrayBlockingQueue<Pair<String, (String) -> Unit>>(DIAGNOSTIC_LINES)
    private val reading = thread(isDaemon = true, name = "semantic-stderr") {
        reader.use { stream ->
            try {
                read(stream)
            } catch (_: IOException) {
                lock.withLock {
                    flushTail()
                    forward("Semantic diagnostics stream closed")
                }
            }
        }
    }
    private val forwarding = thread(isDaemon = true, name = "semantic-stderr-forwarding") {
        while (!closed() || reading.isAlive || forwarded.isNotEmpty()) {
            forwarded.poll(DRAIN_MILLIS, TimeUnit.MILLISECONDS)?.let { (line, destination) ->
                try {
                    destination(line)
                } finally {
                    lock.withLock {
                        pending--
                        changed.signalAll()
                    }
                }
            }
        }
    }

    private fun read(stream: BufferedReader) {
        val buffer = CharArray(DIAGNOSTIC_TAIL_CHARACTERS)
        while (true) {
            lock.withLock {
                when {
                    stream.ready() -> {
                        val count = routing.read(stream, buffer)
                        if (count < 0) {
                            flushTail()
                            return
                        }
                        consume(buffer, count)
                        changed.signalAll()
                    }

                    closed() || !process.isAlive -> {
                        flushTail()
                        return
                    }

                    else -> changed.await(READ_MILLIS, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    private fun consume(buffer: CharArray, count: Int) {
        for (index in 0 until count) {
            if (tail.isEmpty() && !discarding) tailSink = sink
            when {
                buffer[index] == '\n' -> {
                    if (!discarding) flushLine()
                    discarding = false
                }

                !discarding -> {
                    tail.append(buffer[index])
                    if (tail.length == DIAGNOSTIC_TAIL_CHARACTERS) {
                        tail.setLength(0)
                        retain(OMITTED_LINE)
                        forward(OMITTED_LINE, tailSink)
                        discarding = true
                    }
                }
            }
        }
    }

    private fun flushTail() {
        if (tail.isNotEmpty()) flushLine()
    }

    private fun flushLine() {
        val line = tail.toString().removeSuffix("\r")
        tail.setLength(0)
        retain(line)
        forward(line, tailSink)
    }

    private fun forward(line: String, destination: (String) -> Unit = sink) {
        if (forwarded.offer(line to destination)) {
            pending++
            changed.signalAll()
        }
    }

    fun route(query: Boolean, destination: (String) -> Unit, remainingNanos: () -> Long) {
        val limit = System.nanoTime() + minOf(
            remainingNanos(),
            if (query) Long.MAX_VALUE else TimeUnit.MILLISECONDS.toNanos(DRAIN_MILLIS),
        )
        val remaining = { maxOf(limit - System.nanoTime(), 0L) }
        if (!lock.tryLock(remaining(), TimeUnit.NANOSECONDS)) {
            throw TimeoutException("Semantic diagnostic handoff deadline exceeded")
        }
        try {
            while (previousQuery != query && (reader.ready() || pending != 0)) {
                if (remaining() == 0L) throw TimeoutException("Semantic diagnostic handoff deadline exceeded")
                changed.signalAll()
                changed.awaitNanos(remaining())
            }
            sink = destination
            previousQuery = query
        } finally {
            lock.unlock()
        }
    }

    fun await(remainingNanos: () -> Long, queried: Boolean) {
        TimeUnit.NANOSECONDS.timedJoin(reading, remainingNanos())
        if (queried) TimeUnit.NANOSECONDS.timedJoin(forwarding, remainingNanos())
    }

    companion object {
        const val DRAIN_MILLIS = 1000L
        private const val READ_MILLIS = 10L
        private const val DIAGNOSTIC_LINES = 16
        private const val DIAGNOSTIC_TAIL_CHARACTERS = 8192
        private const val OMITTED_LINE = "Semantic diagnostic line over 8192 characters omitted"
    }
}
