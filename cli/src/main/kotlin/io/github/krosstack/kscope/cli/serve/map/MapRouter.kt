package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.service.page.StatusPageRequest
import java.security.MessageDigest

/** Answers the requests of the map: only a GET, for this host, below the path that holds the token. */
internal class MapRouter(
    private val token: String,
    private val port: Int,
    private val assets: MapAssets,
    private val api: MapApi,
) {
    fun answer(request: StatusPageRequest?): MapResponse = when {
        request == null -> MapResponse.error(BAD_REQUEST, "Bad Request")
        request.method != "GET" -> MapResponse.error(METHOD_NOT_ALLOWED, "Method Not Allowed", ALLOW_GET)
        request.host !in allowedHosts() -> MapResponse.error(FORBIDDEN, "Forbidden")
        else -> route(request.target)
    }

    private fun route(target: String): MapResponse {
        val rest = belowToken(target.substringBefore('?')) ?: return MapResponse.error(NOT_FOUND, "Not Found")
        return when {
            rest.isEmpty() -> assets.index()
            rest.startsWith(ASSETS) -> assets.file(rest.removePrefix(ASSETS))
            rest.startsWith(API) -> api(rest.removePrefix(API), target.substringAfter('?', ""))
            else -> MapResponse.error(NOT_FOUND, "Not Found")
        }
    }

    private fun api(route: String, query: String): MapResponse {
        val values = MapQuery.parse(query) ?: return MapResponse.error(BAD_REQUEST, "Bad Request")
        return api.answer(route, values)
    }

    private fun belowToken(path: String): String? {
        val prefix = "/$token/"
        val start = path.take(prefix.length)
        val same = MessageDigest.isEqual(start.toByteArray(Charsets.UTF_8), prefix.toByteArray(Charsets.UTF_8))
        return if (same) path.substring(prefix.length) else null
    }

    private fun allowedHosts(): Set<String> = setOf("127.0.0.1:$port", "localhost:$port")

    private companion object {
        const val ASSETS = "app/"
        const val API = "api/"
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        val ALLOW_GET = mapOf("Allow" to "GET")
    }
}
