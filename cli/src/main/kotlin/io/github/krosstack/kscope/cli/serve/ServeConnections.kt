package io.github.krosstack.kscope.cli.serve

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class ServeConnections(
    private val socket: ServerSocket,
    private val settings: ServeSettings,
    private val authenticated: (String) -> Boolean,
    private val onQueued: () -> Unit,
) : AutoCloseable {
    private val waiting = ArrayBlockingQueue<PendingConnection>(MAX_WAITING)
    private val reading = ConcurrentHashMap.newKeySet<Socket>()
    private val readers = ThreadPoolExecutor(
        MAX_READING,
        MAX_READING,
        0,
        TimeUnit.MILLISECONDS,
        SynchronousQueue(),
        { task -> Thread(task, "kscope-serve-read").apply { isDaemon = true } },
    )
    private val stopping = AtomicBoolean()
    private val acceptor = Thread(::accept, "kscope-serve-accept").apply { isDaemon = true }

    @Volatile
    var lastRequestAt = settings.clock()
        private set

    fun start() = acceptor.start()

    fun next(betweenRequests: () -> Unit = {}): PendingConnection? {
        while (!stopping.get()) {
            val connection = waiting.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)
            if (connection != null) {
                if (connection.request.action == "command") betweenRequests()
                return connection
            }
            if (idleExpired()) stop() else betweenRequests()
        }
        return null
    }

    fun receivedRequest() {
        lastRequestAt = settings.clock()
    }

    fun stop() {
        stopping.set(true)
        socket.close()
    }

    override fun close() {
        stop()
        acceptor.join()
        reading.forEach(Socket::close)
        readers.shutdownNow()
        check(readers.awaitTermination(settings.requestTimeoutMillis.toLong(), TimeUnit.MILLISECONDS))
        val abandoned = mutableListOf<PendingConnection>()
        waiting.drainTo(abandoned)
        abandoned.forEach { it.socket.close() }
    }

    private fun accept() {
        try {
            while (!stopping.get()) {
                val connection = socket.accept()
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.requestTimeoutMillis.toLong())
                startReading(connection, deadline)
                settings.onConnection()
            }
        } catch (_: IOException) {
            stopping.set(true)
        }
    }

    private fun startReading(connection: Socket, deadline: Long) {
        if (waiting.remainingCapacity() == 0) {
            connection.close()
            return
        }
        reading.add(connection)
        try {
            readers.execute { read(connection, deadline) }
        } catch (_: RejectedExecutionException) {
            reading.remove(connection)
            connection.close()
        }
    }

    private fun read(connection: Socket, deadline: Long) {
        var queued = false
        try {
            val request = readRequest(connection, deadline) ?: return
            if (!authenticated(request.token)) return
            queued = waiting.offer(PendingConnection(connection, request))
            if (queued) onQueued()
        } finally {
            reading.remove(connection)
            if (!queued) connection.close()
        }
    }

    private fun readRequest(connection: Socket, deadline: Long): ServeRequest? = try {
        ServeWire.readRequest(connection, deadline)
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun idleExpired(): Boolean = settings.idleTimeoutMillis > 0 &&
        settings.clock() - lastRequestAt >= TimeUnit.MILLISECONDS.toNanos(settings.idleTimeoutMillis)

    companion object {
        const val MAX_WAITING = 16
        private const val MAX_READING = 16
        private const val POLL_MILLIS = 50L
    }
}
