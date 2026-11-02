package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.service.page.StatusPageRequest
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The HTTP server of the map. It listens on the loopback address only, in a path that holds a random token, and it
 * keeps at most [HANDLERS] connections: a connection that finds none free is closed at once.
 */
internal class MapServer private constructor(
    private val socket: ServerSocket,
    token: String,
    private val host: MapHost,
    assets: MapAssets,
) : AutoCloseable {
    private val router = MapRouter(token, socket.localPort, assets, MapApi(host))
    private val events = MapEvents(host.states)
    private val live = ConcurrentHashMap.newKeySet<Socket>()
    private val handlers = ThreadPoolExecutor(
        HANDLERS,
        HANDLERS,
        0,
        TimeUnit.MILLISECONDS,
        SynchronousQueue(),
        { task -> Thread(task, "kscope-map").apply { isDaemon = true } },
    )
    private val acceptor = Thread(::accept, "kscope-map-accept").apply { isDaemon = true }
    val url = "http://127.0.0.1:${socket.localPort}/$token/"
    val boundAddress: InetAddress get() = socket.inetAddress

    fun start() = acceptor.start()

    override fun close() {
        socket.close()
        host.close()
        if (acceptor.isAlive) acceptor.join()
        live.forEach { runCatching(it::close) }
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
        live.add(connection)
        try {
            handlers.execute { answer(connection) }
        } catch (_: RejectedExecutionException) {
            live.remove(connection)
            connection.close()
        }
    }

    private fun answer(connection: Socket) {
        try {
            connection.use { client ->
                client.soTimeout = READ_TIMEOUT_MILLIS
                val request = StatusPageRequest.read(client.getInputStream())
                send(client, router.answer(request))
            }
        } catch (_: IOException) {
            return
        } finally {
            live.remove(connection)
        }
    }

    private fun send(client: Socket, response: MapResponse) {
        val output = client.getOutputStream()
        when (response) {
            is MapResponse.Plain -> output.write(response.toBytes())

            MapResponse.Events -> {
                output.write(MapResponse.Events.head())
                output.flush()
                events.stream(output)
            }
        }
        output.flush()
    }

    companion object {
        const val HANDLERS = 32
        private const val BACKLOG = 16
        private const val READ_TIMEOUT_MILLIS = 5_000
        private const val GRACE_SECONDS = 5L

        fun open(token: String, host: MapHost, assets: MapAssets = MapAssets()): MapServer {
            val listener = ServerSocket()
            try {
                listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), BACKLOG)
                return MapServer(listener, token, host, assets)
            } catch (failure: IOException) {
                listener.close()
                throw failure
            }
        }
    }
}
