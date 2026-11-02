package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

internal object ServeClient {
    const val VERSION_NOTE = "A server of another version runs; stop it with `kscope serve --stop` for this workspace."
    private const val CONNECT_TIMEOUT_MILLIS = 500

    fun compatible(metadata: ServeMetadata): Boolean =
        metadata.toolVersion == KrosScope.toolVersion() && metadata.protocol == ServeWire.PROTOCOL

    fun alive(metadata: ServeMetadata): Boolean = metadata.pid > 0 &&
        ProcessHandle.of(metadata.pid).map { it.isAlive }.orElse(false) && portOpen(metadata.port)

    fun exchange(
        metadata: ServeMetadata,
        request: ServeRequest,
        requireCompatible: Boolean = true,
        answerTimeoutMillis: Int = 0,
        output: (ServeResponse) -> Unit,
    ): Int? {
        var answered = false
        return try {
            connect(metadata.port).use { socket ->
                socket.soTimeout = answerTimeoutMillis
                val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                writer.write(ServeWire.encode(request) + "\n")
                writer.flush()
                readAnswer(socket, requireCompatible, { answered = true }, output)
            }
        } catch (_: IOException) {
            if (answered) ExitCodes.FAILURE else null
        } catch (_: SerializationException) {
            if (answered) ExitCodes.FAILURE else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun connect(port: Int): Socket = Socket().also { socket ->
        try {
            socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), CONNECT_TIMEOUT_MILLIS)
        } catch (failure: IOException) {
            socket.close()
            throw failure
        }
    }

    private fun portOpen(port: Int): Boolean = try {
        connect(port).use { true }
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun readAnswer(
        socket: Socket,
        requireCompatible: Boolean,
        answered: () -> Unit,
        output: (ServeResponse) -> Unit,
    ): Int? {
        val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        val hello = reader.readLine()?.let { ServeWire.json.decodeFromString<ServeResponse>(it) } ?: return null
        return if (hello.kind != "hello") {
            null
        } else {
            answered()
            socket.soTimeout = 0
            val compatible = hello.toolVersion == KrosScope.toolVersion() && hello.protocol == ServeWire.PROTOCOL
            if (requireCompatible && !compatible) VERSION_MISMATCH else readChunks(reader, output)
        }
    }

    private fun readChunks(reader: java.io.BufferedReader, output: (ServeResponse) -> Unit): Int {
        var exitCode = ExitCodes.FAILURE
        var finished = false
        while (!finished) {
            val line = reader.readLine() ?: break
            val response = ServeWire.json.decodeFromString<ServeResponse>(line)
            when (response.kind) {
                "exit" -> {
                    exitCode = response.exitCode
                    finished = true
                }

                "output" -> output(response)

                else -> finished = true
            }
        }
        return exitCode
    }

    const val VERSION_MISMATCH = -1
}
