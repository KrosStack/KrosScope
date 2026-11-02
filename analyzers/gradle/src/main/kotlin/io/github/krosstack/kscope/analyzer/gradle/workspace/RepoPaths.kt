package io.github.krosstack.kscope.analyzer.gradle.workspace

/** Arithmetic on normalized repository-relative paths, where the empty path is the repository root. */
internal object RepoPaths {
    fun join(base: String, child: String): String = listOf(base, child).filter { it.isNotEmpty() }.joinToString("/")

    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    fun isWithin(path: String, directory: String): Boolean =
        directory.isEmpty() || path == directory || path.startsWith("$directory/")

    /** Resolves a relative path against [base]; null when it leaves the repository or is not relative. */
    fun resolve(base: String, relative: String): String? {
        val isRelative = !relative.startsWith("/") && !relative.startsWith("\\") && relative.getOrNull(1) != ':'
        return if (isRelative) normalize(base.split('/') + relative.replace('\\', '/').split('/')) else null
    }

    /** The directory of a Gradle project path such as `:core:model`, below [root]. */
    fun directoryOf(root: String, projectPath: String): String = join(root, projectPath.trim(':').replace(':', '/'))

    private fun normalize(parts: List<String>): String? {
        val segments = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(part)
            }
        }
        return segments.joinToString("/")
    }
}
