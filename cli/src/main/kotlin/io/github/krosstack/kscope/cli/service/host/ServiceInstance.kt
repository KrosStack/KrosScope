package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeConnections
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeWire
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import io.github.krosstack.kscope.cli.service.home.ServiceLock
import io.github.krosstack.kscope.cli.service.home.ServiceRefusal
import io.github.krosstack.kscope.cli.service.page.StatusPageHtml
import io.github.krosstack.kscope.cli.service.page.StatusPageServer
import io.github.krosstack.kscope.service.KrosScope
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CountDownLatch

internal class ServiceInstance private constructor(
    private val directory: ServiceDirectory,
    private val lock: ServiceLock,
    socket: ServerSocket,
    settings: ServiceSettings,
    private val initial: ServiceFileReading.Read,
) : AutoCloseable {
    val metadata = ServeMetadata(
        ProcessHandle.current().pid(),
        socket.localPort,
        ServeServer.randomToken(),
        KrosScope.toolVersion(),
        ServeWire.PROTOCOL,
        Instant.now().toString(),
    )
    private val host = ServiceHost(settings)
    private val snapshot = ServiceStatusSnapshot(metadata, host, settings.heap)
    private val page = StatusPageServer.open(ServeServer.randomToken()) { StatusPageHtml.render(snapshot.take()) }
    private val control = ServiceControl(
        ServeConnections(socket, settings.serve.copy(idleTimeoutMillis = 0), ::authenticated) {},
        host,
        directory.file,
        ::release,
        page.url,
    )
    private val finished = CountDownLatch(1)
    private var released = false

    fun run() {
        host.start(initial.workspaces.map { Path.of(it) }, initial.limits)
        page.start()
        try {
            control.run()
        } finally {
            try {
                close()
            } finally {
                finished.countDown()
            }
        }
    }

    fun requestStop() = control.requestStop()

    fun awaitTermination() = finished.await()

    fun retryNow() = host.retryNow()

    fun look() = host.look()

    val pageUrl: String get() = page.url

    val pageAddress: InetAddress get() = page.boundAddress

    override fun close() {
        try {
            release()
        } finally {
            control.close()
        }
    }

    @Synchronized
    private fun release() {
        if (released) return
        released = true
        try {
            host.stop()
        } finally {
            try {
                Files.deleteIfExists(directory.metadataFile)
            } finally {
                closePageThenLock()
            }
        }
    }

    private fun closePageThenLock() {
        try {
            page.close()
        } finally {
            lock.close()
        }
    }

    private fun authenticated(token: String): Boolean = MessageDigest.isEqual(
        metadata.token.toByteArray(Charsets.US_ASCII),
        token.toByteArray(Charsets.US_ASCII),
    )

    companion object {
        fun open(directory: ServiceDirectory, settings: ServiceSettings): ServiceInstance {
            Files.createDirectories(directory.root)
            val lock = ServiceLock.acquire(directory.lockFile)
            var socket: ServerSocket? = null
            var ready = false
            try {
                val kept = keptIn(directory.file)
                val listener = ServerSocket().also { socket = it }
                listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), ServeConnections.MAX_WAITING)
                val opened = ServiceInstance(directory, lock, listener, settings, kept)
                opened.metadata.writeTo(directory.metadataFile)
                ready = true
                return opened
            } finally {
                if (!ready) closeIncomplete(socket, lock)
            }
        }

        private fun keptIn(file: ServiceFile): ServiceFileReading.Read = when (val reading = file.read()) {
            is ServiceFileReading.Read -> reading
            is ServiceFileReading.Unreadable -> throw ServiceRefusal("error: ${reading.message}")
        }

        private fun closeIncomplete(socket: ServerSocket?, lock: ServiceLock) {
            try {
                socket?.close()
            } finally {
                lock.close()
            }
        }
    }
}
