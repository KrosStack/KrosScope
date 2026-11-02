package io.github.krosstack.kscope.cli.serve.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.net.Socket

/** A viewer: it reads the head of the answer, then the events one at a time. */
internal class EventStream(address: MapAddress) : AutoCloseable {
    private val socket = Socket("127.0.0.1", address.port).apply { soTimeout = MapAddress.READ_TIMEOUT_MILLIS }
    private val reader: BufferedReader
    val head: String

    init {
        val request = "GET ${address.target("api/events")} HTTP/1.1\r\nHost: 127.0.0.1:${address.port}\r\n\r\n"
        socket.getOutputStream().apply {
            write(request.toByteArray())
            flush()
        }
        reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.joinToString("\n")
    }

    /** The next state that the server sent, or null when the stream ended. */
    fun nextState(): JsonObject? {
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.startsWith("data: ")) return Json.parseToJsonElement(line.removePrefix("data: ")).jsonObject
        }
    }

    /** Everything that the server sends until it ends the stream. */
    fun readToEnd(): String = reader.readText()

    override fun close() = socket.close()
}
