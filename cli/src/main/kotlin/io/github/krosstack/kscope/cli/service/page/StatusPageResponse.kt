package io.github.krosstack.kscope.cli.service.page

internal data class StatusPageResponse(
    val status: Int,
    val reason: String,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    fun toBytes(): ByteArray {
        val content = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            (SECURITY_HEADERS + headers).forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${content.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        return head.toByteArray(Charsets.ISO_8859_1) + content
    }

    companion object {
        private const val OK = 200

        private val SECURITY_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
        )

        fun page(body: String) = StatusPageResponse(OK, "OK", body)

        fun error(status: Int, reason: String, headers: Map<String, String> = emptyMap()) =
            StatusPageResponse(status, reason, "<!DOCTYPE html><title>$reason</title><p>$reason</p>\n", headers)
    }
}
