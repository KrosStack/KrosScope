package io.github.krosstack.kscope.cli.service.page

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class StatusPageServer private constructor(
    private val socket: ServerSocket,
    token: String,
    render: () -> String,
) : AutoCloseable {
    private val router = StatusPageRouter(token, socket.localPort, render)
    private val handlers = ThreadPoolExecutor(
        HANDLERS,
        HANDLERS,
        0,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(WAITING),
        { task -> Thread(task, "kscope-service-page").apply { isDaemon = true } },
    )
    private val acceptor = Thread(::accept, "kscope-service-page-accept").apply { isDaemon = true }
    val url = "http://127.0.0.1:${socket.localPort}/$token/"
    val boundAddress: InetAddress get() = socket.inetAddress

    fun start() = acceptor.start()

    override fun close() {
        socket.close()
        if (acceptor.isAlive) acceptor.join()
        handlers.shutdownNow()
        handlers.awaitTermination(GRACE_SECONDS, TimeUnit.SECONDS)
    }

    private fun accept() {
        try {
            while (true) hand(socket.accept())
        } catch (_: IOException) {
            return
        }
    }

    private fun hand(connection: Socket) {
        try {
            handlers.execute { answer(connection) }
        } catch (_: RejectedExecutionException) {
            connection.close()
        }
    }

    private fun answer(connection: Socket) {
        connection.use { client ->
            try {
                client.soTimeout = READ_TIMEOUT_MILLIS
                val request = StatusPageRequest.read(client.getInputStream())
                client.getOutputStream().apply {
                    write(router.answer(request).toBytes())
                    flush()
                }
            } catch (_: IOException) {
                return
            }
        }
    }

    companion object {
        private const val HANDLERS = 4
        private const val WAITING = 8
        private const val READ_TIMEOUT_MILLIS = 5_000
        private const val GRACE_SECONDS = 5L

        fun open(token: String, render: () -> String): StatusPageServer {
            val listener = ServerSocket()
            try {
                listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), WAITING)
                return StatusPageServer(listener, token, render)
            } catch (failure: IOException) {
                listener.close()
                throw failure
            }
        }
    }
}
