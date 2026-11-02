package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.serve.map.MapHost
import io.github.krosstack.kscope.cli.serve.map.MapServer
import io.github.krosstack.kscope.cli.serve.watch.ServeWatch
import io.github.krosstack.kscope.cli.serve.watch.WatchState
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.SerializationException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

internal class ServeServer private constructor(
    val workspace: Path,
    private val lock: ServeLock,
    private val socket: ServerSocket,
    settings: ServeSettings,
    private val requests: ServeRequests,
) : AutoCloseable {
    private val connections = ServeConnections(socket, settings, ::authenticated, requests::queued)
    private val watcher = if (settings.watchEnabled) ServeWatch(workspace, settings) else null
    val watchState: WatchState? get() = watcher?.state
    private val mapHost = if (settings.mapEnabled) MapHost(workspace, watchState, settings.clock) else null
    private val mapServer = mapHost?.let { MapServer.open(randomToken(), it) }
    val mapUrl: String? get() = mapServer?.url
    private val hosted = settings.hosted
    val releases = ServeReleases(hosted)
    val loaded: Boolean get() = requests.loaded
    val indexed: Boolean get() = requests.indexed
    val lastScan: ScanRecord? get() = requests.lastScan
    val indexLevel: String? get() = requests.indexLevel
    val lastRequestAt: Long get() = connections.lastRequestAt
    private val closed = AtomicBoolean()
    private val finished = CountDownLatch(1)
    val boundAddress: InetAddress get() = socket.inetAddress
    val requestCount: Int get() = requests.count
    val metadata = ServeMetadata(
        ProcessHandle.current().pid(),
        socket.localPort,
        randomToken(),
        KrosScope.toolVersion(),
        ServeWire.PROTOCOL,
        Instant.now().toString(),
    )

    fun run() {
        connections.start()
        watcher?.start()
        mapServer?.start()
        try {
            while (true) {
                val connection = connections.next(::updateBetweenRequests) ?: break
                serve(connection)
            }
        } finally {
            try {
                close()
            } finally {
                finished.countDown()
            }
        }
    }

    fun requestStop() = connections.stop()

    fun awaitTermination() = finished.await()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            try {
                try {
                    mapServer?.close()
                } finally {
                    connections.close()
                }
            } finally {
                try {
                    watcher?.close()
                } finally {
                    releaseIndex()
                }
            }
        } finally {
            lock.close()
        }
    }

    private fun releaseIndex() {
        try {
            requests.close()
        } finally {
            metadata.deleteFrom(workspace)
        }
    }

    private fun updateBetweenRequests() {
        if (releases.takeRequested()) requests.release()
        mapHost?.tick(requests.service)
        val state = watchState ?: return
        if (state.eligibilityCheckWanted) {
            runCatching { requests.refreshEligibility(state) }.onFailure { state.setTrusted(true) }
        }
        if (state.updateWanted) requests.runUpdate(state)
    }

    private fun serve(connection: PendingConnection) {
        connection.socket.use { client ->
            try {
                val request = connection.request
                connections.receivedRequest()
                val output = ServeOutput(client.getOutputStream().bufferedWriter(Charsets.UTF_8))
                answer(request, output)
                connections.receivedRequest()
            } catch (_: java.io.IOException) {
                Unit
            } catch (_: SerializationException) {
                Unit
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
    }

    private fun answer(request: ServeRequest, output: ServeOutput) {
        output.send(ServeResponse("hello"))
        val compatible = request.toolVersion == metadata.toolVersion && request.protocol == metadata.protocol
        val code = if (request.action == "command" && !compatible) ExitCodes.FAILURE else dispatch(request, output)
        output.send(ServeResponse("exit", exitCode = code))
    }

    private fun authenticated(token: String): Boolean = MessageDigest.isEqual(
        metadata.token.toByteArray(Charsets.US_ASCII),
        token.toByteArray(Charsets.US_ASCII),
    )

    private fun dispatch(request: ServeRequest, output: ServeOutput): Int = when (request.action) {
        "status" -> {
            watchState?.let { output.write(it.status() + "\n", false) }
            mapUrl?.let { output.write("Map: $it\n", false) }
            0
        }

        "stop" -> stop(output)

        "command" -> requests.execute(request, output)

        else -> {
            output.write("This command is not served.\n", true)
            ExitCodes.USAGE
        }
    }

    private fun stop(output: ServeOutput): Int {
        if (hosted) {
            output.write(HOSTED_STOP_REFUSAL + "\n", true)
            return ExitCodes.FAILURE
        }
        requestStop()
        close()
        return 0
    }

    companion object {
        const val HOSTED_STOP_REFUSAL = "This workspace is kept by the service. " +
            "Run `kscope service remove` for it, or `kscope service stop`."

        fun open(
            workspace: Path,
            settings: ServeSettings = ServeSettings(),
            scanRunner: ScanRunner = ScanRunner.DEFAULT,
            onQueued: () -> Unit = {},
        ): ServeServer {
            val root = workspace.toRealPath()
            val lock = ServeLock.acquire(root)
            var socket: ServerSocket? = null
            var server: ServeServer? = null
            var ready = false
            try {
                val listener = ServerSocket().also { socket = it }
                listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), ServeConnections.MAX_WAITING)
                val requests = ServeRequests(root, settings.onRequest, scanRunner, onQueued, settings.onLoadChange)
                val opened = ServeServer(root, lock, listener, settings, requests).also { server = it }
                opened.metadata.write(root)
                opened.requests.warm()
                opened.requests.watch = opened.watchState
                opened.requests.map = opened.mapHost
                opened.requests.watchSettings = settings.watch
                opened.watchState?.let { opened.requests.refreshEligibility(it) }
                ready = true
                return opened
            } finally {
                if (!ready) {
                    closeIncomplete(server, socket, lock)
                }
            }
        }

        private fun closeIncomplete(server: ServeServer?, socket: ServerSocket?, lock: ServeLock) {
            if (server != null) {
                server.close()
            } else {
                try {
                    socket?.close()
                } finally {
                    lock.close()
                }
            }
        }

        fun randomToken(): String {
            val bytes = ByteArray(TOKEN_BYTES)
            SecureRandom().nextBytes(bytes)
            return HexFormat.of().formatHex(bytes)
        }

        private const val TOKEN_BYTES = 32
    }
}

private fun ServeRequests.refreshEligibility(state: WatchState) {
    state.setTrusted(refreshTrust())
    state.shallow = refreshDepth()
}

private fun ServeRequests.runUpdate(state: WatchState) {
    val code = runCatching {
        // The trust can be revoked after the last look, so it is read again right before the build can start.
        refreshEligibility(state)
        if (!state.beginUpdate()) return
        update()
    }.getOrDefault(ExitCodes.FAILURE)
    state.shallow = runCatching { refreshDepth() }.getOrDefault(false)
    state.updateCompleted(code, runCatching { refreshTrust() }.getOrDefault(true))
}
