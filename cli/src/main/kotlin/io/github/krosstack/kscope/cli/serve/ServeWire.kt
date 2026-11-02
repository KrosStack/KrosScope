package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.model.KscopeJson
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

internal object ServeWire {
    const val PROTOCOL = 1
    const val MAX_REQUEST_BYTES = 1_048_576
    const val REQUEST_TIMEOUT_MILLIS = 10_000
    val json = KscopeJson

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    fun readRequest(socket: Socket, deadline: Long): ServeRequest? {
        val bytes = ByteArrayOutputStream()
        val input = socket.getInputStream().buffered()
        while (bytes.size() <= MAX_REQUEST_BYTES) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw SocketTimeoutException("Request deadline exceeded")
            socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1).toInt()
            val next = input.read()
            if (next < 0) return null
            if (next == '\n'.code) return json.decodeFromString<ServeRequest>(bytes.toString(Charsets.UTF_8))
            bytes.write(next)
        }
        throw IOException("Request line exceeds the limit")
    }
}
