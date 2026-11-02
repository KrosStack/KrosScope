package io.github.krosstack.kscope.cli.serve.map

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal sealed interface MapResponse {
    /** An answer of one piece, with its length, after which the connection closes. */
    class Plain(
        val status: Int,
        val reason: String,
        val contentType: String,
        val body: ByteArray,
        val headers: Map<String, String> = emptyMap(),
    ) : MapResponse {
        fun toBytes(): ByteArray {
            val head = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                (SECURITY_HEADERS + headers).forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            return head.toByteArray(Charsets.ISO_8859_1) + body
        }
    }

    /** The stream of events that a viewer keeps open. */
    data object Events : MapResponse {
        fun head(): ByteArray = buildString {
            append("HTTP/1.1 200 OK\r\n")
            SECURITY_HEADERS.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
    }

    companion object {
        private const val OK = 200

        val SECURITY_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
            "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self'; " +
                "connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; " +
                "form-action 'none'",
        )

        fun ok(contentType: String, body: ByteArray, headers: Map<String, String> = emptyMap()): MapResponse =
            Plain(OK, "OK", contentType, body, headers)

        fun json(body: ByteArray, headers: Map<String, String> = emptyMap()): MapResponse = ok(JSON, body, headers)

        fun json(body: String): MapResponse = json(body.toByteArray(Charsets.UTF_8))

        /** A refusal of a client of the map, with a sentence that the page can show. */
        fun failure(status: Int, reason: String, message: String): MapResponse = Plain(
            status,
            reason,
            JSON,
            Json.encodeToString(mapOf("error" to message)).toByteArray(Charsets.UTF_8),
        )

        fun error(status: Int, reason: String, headers: Map<String, String> = emptyMap()): MapResponse =
            Plain(status, reason, "text/plain; charset=utf-8", "$reason\n".toByteArray(Charsets.UTF_8), headers)

        const val JSON = "application/json; charset=utf-8"
    }
}
