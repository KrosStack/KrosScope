package io.github.krosstack.kscope.cli.serve.map

/**
 * Serves the files of the page, and only those that the `MANIFEST` of the directory of resources lists. A name that
 * is not in the list is not looked up at all, so no name can leave the directory.
 */
internal class MapAssets(private val read: (String) -> ByteArray? = ::packaged) {
    private val allowed: Set<String> by lazy {
        read(MANIFEST)?.toString(Charsets.UTF_8).orEmpty().lines().map { it.trim() }
            .filter { it.isNotEmpty() && it != MANIFEST && '/' !in it && '\\' !in it && ".." !in it }.toSet()
    }

    fun index(): MapResponse = file(INDEX)

    fun file(name: String): MapResponse {
        val content = name.takeIf { it in allowed }?.let(read) ?: return MapResponse.error(NOT_FOUND, "Not Found")
        return MapResponse.ok(contentType(name), content)
    }

    private fun contentType(name: String): String = when (name.substringAfterLast('.', "")) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MANIFEST = "MANIFEST"
        const val INDEX = "index.html"
        const val NOT_FOUND = 404
        const val DIRECTORY = "/io/github/krosstack/kscope/cli/serve/map/"

        fun packaged(name: String): ByteArray? =
            MapAssets::class.java.getResourceAsStream(DIRECTORY + name)?.use { it.readAllBytes() }
    }
}
