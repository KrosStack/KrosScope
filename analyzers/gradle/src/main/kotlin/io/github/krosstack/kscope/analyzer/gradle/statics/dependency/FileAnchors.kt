package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

private val absolute = Regex("""^(?:/|[A-Za-z]:/)""")

/**
 * Turns a declared file into a path relative to the repository, from the directory of its project and of its build
 * root. A path may leave the repository, as `../other/build/libs/other.jar` does; an absolute path is not related.
 */
internal class FileAnchors(private val projectDirectory: String, private val rootDirectory: String) {
    fun pathOf(file: DeclaredFile): String? {
        val written = file.path.removeSuffix("/")
        if (absolute.containsMatchIn(written) || written.isEmpty()) return null
        val base = if (file.fromRoot) rootDirectory else projectDirectory
        val segments = ArrayDeque<String>()
        (base.split('/') + written.split('/')).filter { it.isNotEmpty() && it != "." }.forEach { segment ->
            if (segment == ".." && segments.isNotEmpty() && segments.last() != "..") {
                segments.removeLast()
            } else {
                segments.addLast(segment)
            }
        }
        return segments.joinToString("/").takeIf { it.isNotEmpty() }
    }
}
