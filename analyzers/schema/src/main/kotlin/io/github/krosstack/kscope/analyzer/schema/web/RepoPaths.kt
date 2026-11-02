package io.github.krosstack.kscope.analyzer.schema.web

private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/** Joins and normalizes repository paths. A path that leaves the repository, or is not relative, has no value. */
internal object RepoPaths {
    fun directoryOf(path: String): String = path.substringBeforeLast('/', "")

    fun hasScheme(reference: String): Boolean = scheme.containsMatchIn(reference)

    fun resolve(directory: String, relative: String): String? {
        if (relative.startsWith('/') || '\\' in relative || hasScheme(relative)) return null
        val start: List<String>? = directory.split('/').filter { it.isNotEmpty() }
        return relative.split('/').fold(start) { path, segment ->
            when {
                path == null -> null
                segment.isEmpty() || segment == "." -> path
                segment == ".." -> path.dropLast(1).takeIf { path.isNotEmpty() }
                else -> path + segment
            }
        }?.joinToString("/")
    }

    fun isInside(path: String, directory: String): Boolean = directory.isEmpty() || path.startsWith("$directory/")

    fun isAtOrInside(path: String, directory: String): Boolean = path == directory || isInside(path, directory)
}
