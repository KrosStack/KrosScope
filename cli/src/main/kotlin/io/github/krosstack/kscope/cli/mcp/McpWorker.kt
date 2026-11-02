package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class McpWorker(private val streams: McpStreams, private val answer: (McpRequest) -> JsonObject?) :
    AutoCloseable {
    private val lock = ReentrantLock()
    private val available = lock.newCondition()
    private val waiting = ArrayDeque<McpPendingRequest>()
    private var running: McpPendingRequest? = null
    private var closed = false
    private var failure: Throwable? = null
    private val thread = Thread(::work, "kscope-mcp-worker")

    fun start() = thread.start()

    fun offer(request: McpPendingRequest): Unit = lock.withLock {
        failure?.let { throw it }
        if (waiting.size < MAX_WAITING) {
            waiting.addLast(request)
            available.signal()
        } else {
            streams.respond(McpResults.overloaded(request.id))
        }
    }

    fun cancel(id: JsonElement?): Unit = lock.withLock {
        if (id == null) return
        waiting.removeAll { it.id == id }
        if (running?.id == id) {
            val interrupt = running?.isToolCall == true
            running = null
            if (interrupt) thread.interrupt()
        }
    }

    override fun close() {
        lock.withLock {
            closed = true
            available.signal()
        }
        thread.join()
        failure?.let { throw it }
    }

    private fun work() {
        runCatching { drain() }.onFailure {
            lock.withLock { failure = it }
        }
    }

    private fun drain() {
        while (true) {
            val request = next() ?: return
            val reply = request.error ?: request.request?.let(answer)
            lock.withLock {
                if (running === request) {
                    streams.respond(reply)
                    running = null
                }
                Thread.interrupted()
            }
        }
    }

    private fun next(): McpPendingRequest? = lock.withLock {
        Thread.interrupted()
        while (waiting.isEmpty() && !closed) available.awaitUninterruptibly()
        if (waiting.isEmpty()) {
            null
        } else {
            waiting.removeFirst().also { running = it }
        }
    }

    private companion object {
        const val MAX_WAITING = 64
    }
}
