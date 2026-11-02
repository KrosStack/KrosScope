package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeWire
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.service.KrosScope
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

internal class SilentService : AutoCloseable {
    private val socket = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
    private val accepting = Thread(::acceptAndHangUp, "silent-service").apply {
        isDaemon = true
        start()
    }

    fun announceIn(directory: ServiceDirectory) {
        val metadata = ServeMetadata(
            ProcessHandle.current().pid(),
            socket.localPort,
            "token",
            KrosScope.toolVersion(),
            ServeWire.PROTOCOL,
            "then",
        )
        metadata.writeTo(directory.metadataFile)
    }

    override fun close() {
        socket.close()
        accepting.join()
    }

    private fun acceptAndHangUp() {
        try {
            while (true) socket.accept().close()
        } catch (_: IOException) {
            return
        }
    }

    private companion object {
        const val BACKLOG = 50
    }
}
