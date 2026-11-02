package io.github.krosstack.kscope.cli.serve.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.Socket

internal class HttpAnswer(val status: Int, val headers: Map<String, String>, val body: String) {
    fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject
}

/** The address of a map, and what a browser would send to it. */
internal class MapAddress(val url: String) {
    val port: Int = url.substringAfter("127.0.0.1:").substringBefore('/').toInt()
    val token: String = url.removeSuffix("/").substringAfterLast('/')

    fun target(path: String) = "/$token/$path"

    fun get(path: String, host: String = "127.0.0.1:$port", method: String = "GET"): HttpAnswer =
        raw("$method ${target(path)} HTTP/1.1\r\nHost: $host\r\n\r\n")

    fun raw(request: String): HttpAnswer = Socket("127.0.0.1", port).use { client ->
        client.soTimeout = READ_TIMEOUT_MILLIS
        client.getOutputStream().apply {
            write(request.toByteArray(Charsets.ISO_8859_1))
            flush()
        }
        parse(client.getInputStream().readAllBytes().toString(Charsets.UTF_8))
    }

    fun events(): EventStream = EventStream(this)

    private fun parse(text: String): HttpAnswer {
        val head = text.substringBefore("\r\n\r\n").split("\r\n")
        val headers = head.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        return HttpAnswer(head.first().split(' ')[1].toInt(), headers, text.substringAfter("\r\n\r\n", ""))
    }

    companion object {
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
