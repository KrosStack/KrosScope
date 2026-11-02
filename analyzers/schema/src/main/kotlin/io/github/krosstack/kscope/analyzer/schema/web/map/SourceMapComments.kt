package io.github.krosstack.kscope.analyzer.schema.web.map

private const val MARKER = "sourceMappingURL="
private const val DATA_URL = "data:"

/**
 * Reads the comment at the end of a bundle that says where its source map is. The map is inline when the last such
 * comment holds it in a `data:` URL.
 */
internal object SourceMapComments {
    fun isInline(bundle: String): Boolean {
        val marker = bundle.lastIndexOf(MARKER)
        return marker >= 0 && bundle.startsWith(DATA_URL, marker + MARKER.length)
    }
}
