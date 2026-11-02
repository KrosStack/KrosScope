package io.github.krosstack.kscope.engine.build

/** Finds the value registered for the nearest enclosing directory of a repository path. */
internal class DirectoryIndex<T>(private val valuesByDirectory: Map<String, T>) {
    fun nearest(path: String, includeSelf: Boolean = false): T? {
        val start = if (includeSelf) path else parentOf(path)
        return generateSequence(start) { current -> if (current.isEmpty()) null else parentOf(current) }
            .firstNotNullOfOrNull { valuesByDirectory[it] }
    }
}

internal fun parentOf(path: String): String = path.substringBeforeLast('/', "")
