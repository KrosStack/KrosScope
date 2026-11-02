package io.github.krosstack.kscope.cli.service.page

import java.security.MessageDigest

internal class StatusPageRouter(private val token: String, private val port: Int, private val render: () -> String) {
    fun answer(request: StatusPageRequest?): StatusPageResponse = when {
        request == null -> StatusPageResponse.error(BAD_REQUEST, "Bad Request")
        request.method != "GET" -> StatusPageResponse.error(METHOD_NOT_ALLOWED, "Method Not Allowed", ALLOW_GET)
        request.host !in allowedHosts() -> StatusPageResponse.error(FORBIDDEN, "Forbidden")
        !onPagePath(request.target) -> StatusPageResponse.error(NOT_FOUND, "Not Found")
        else -> StatusPageResponse.page(render())
    }

    private fun allowedHosts(): Set<String> = setOf("127.0.0.1:$port", "localhost:$port")

    private fun onPagePath(target: String): Boolean = MessageDigest.isEqual(
        target.substringBefore('?').toByteArray(Charsets.UTF_8),
        "/$token/".toByteArray(Charsets.UTF_8),
    )

    private companion object {
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        val ALLOW_GET = mapOf("Allow" to "GET")
    }
}
