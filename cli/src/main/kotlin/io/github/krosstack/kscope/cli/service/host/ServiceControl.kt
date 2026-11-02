package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.serve.PendingConnection
import io.github.krosstack.kscope.cli.serve.ServeConnections
import io.github.krosstack.kscope.cli.serve.ServeOutput
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeResponse
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ServiceControl(
    private val connections: ServeConnections,
    private val host: ServiceHost,
    private val file: ServiceFile,
    private val release: () -> Unit,
    private val pageUrl: String,
) : AutoCloseable {
    private val handlers = Executors.newCachedThreadPool { task ->
        Thread(task, "kscope-service-control").apply { isDaemon = true }
    }

    fun run() {
        connections.start()
        try {
            while (true) {
                val connection = connections.next() ?: break
                handlers.execute { answer(connection) }
            }
        } finally {
            handlers.shutdown()
            if (!handlers.awaitTermination(GRACE_SECONDS, TimeUnit.SECONDS)) handlers.shutdownNow()
        }
    }

    fun requestStop() = connections.stop()

    override fun close() = connections.close()

    private fun answer(connection: PendingConnection) {
        connection.socket.use { client ->
            try {
                val output = ServeOutput(client.getOutputStream().bufferedWriter(Charsets.UTF_8))
                output.send(ServeResponse("hello"))
                output.send(ServeResponse("exit", exitCode = dispatch(connection.request, output)))
            } catch (_: IOException) {
                Unit
            } catch (_: SerializationException) {
                Unit
            }
        }
    }

    private fun dispatch(request: ServeRequest, output: ServeOutput): Int = when (request.action) {
        "status" -> {
            host.lines().forEach { output.write("$it\n", false) }
            0
        }

        "reload" -> reload(output)

        "page" -> {
            output.write("$pageUrl\n", false)
            0
        }

        "stop" -> {
            release()
            connections.stop()
            0
        }

        else -> {
            output.write("This command is not served.\n", true)
            ExitCodes.USAGE
        }
    }

    private fun reload(output: ServeOutput): Int = when (val reading = file.read()) {
        is ServiceFileReading.Read -> {
            host.reload(reading.workspaces.map { Path.of(it) }, reading.limits)
            0
        }

        is ServiceFileReading.Unreadable -> {
            output.write("${reading.message}\n", true)
            ExitCodes.FAILURE
        }
    }

    private companion object {
        const val GRACE_SECONDS = 30L
    }
}
