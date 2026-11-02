package io.github.krosstack.kscope.analyzer.schema.web.map

import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths

private const val WEBPACK_SCHEME = "webpack://"

/**
 * Turns the entries of the `sources` of a source map into repository paths. The `sourceRoot` is put before an entry
 * that is relative, with a slash between. An entry is resolved against the directory of the map; one that webpack
 * wrote, as `webpack://<name>/<path>` with the name of the package, against [packageDirectory], the directory of the
 * package that built the bundle, which may be unknown. Any other entry with a scheme, an absolute path, and a path that
 * leaves the repository have no value.
 */
internal class SourceResolver(private val mapDirectory: String, private val packageDirectory: String?) {
    fun resolve(sourceRoot: String, source: String): String? {
        val reference = joined(sourceRoot, source)
        return if (reference.startsWith(WEBPACK_SCHEME)) {
            packageDirectory?.let { RepoPaths.resolve(it, withoutWebpackPrefix(reference)) }
        } else {
            RepoPaths.resolve(mapDirectory, reference)
        }
    }

    private fun joined(sourceRoot: String, source: String): String = when {
        sourceRoot.isEmpty() || source.startsWith('/') || RepoPaths.hasScheme(source) -> source
        sourceRoot.endsWith('/') -> sourceRoot + source
        else -> "$sourceRoot/$source"
    }

    private fun withoutWebpackPrefix(reference: String): String {
        val segments = reference.removePrefix(WEBPACK_SCHEME).split('/')
        val nameSegments = if (segments.first().startsWith('@')) 2 else 1
        return segments.drop(nameSegments).joinToString("/")
    }
}
