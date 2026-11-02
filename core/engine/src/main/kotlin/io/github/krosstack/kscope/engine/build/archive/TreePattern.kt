package io.github.krosstack.kscope.engine.build.archive

/**
 * An Ant-style path pattern as Gradle reads it: `*` and `?` stay within one name, `**` stands for any number of
 * directories, and a trailing slash means everything below. Names are compared case-sensitively.
 */
internal class TreePattern(pattern: String) {
    private val parts = partsOf(pattern)
    private val names = parts.map(::nameRegex)

    fun matches(path: List<String>): Boolean = matchesFrom(0, path, 0)

    private fun matchesFrom(part: Int, path: List<String>, at: Int): Boolean = when {
        part == parts.size -> at == path.size
        parts[part] == ANY_DEPTH -> (at..path.size).any { matchesFrom(part + 1, path, it) }
        at == path.size -> false
        else -> names[part].matches(path[at]) && matchesFrom(part + 1, path, at + 1)
    }

    private fun partsOf(pattern: String): List<String> {
        val written = pattern.split('/').filter(String::isNotEmpty)
        val expanded = if (pattern.endsWith('/')) written + ANY_DEPTH else written
        return expanded.filterIndexed { index, part -> part != ANY_DEPTH || expanded.getOrNull(index - 1) != ANY_DEPTH }
    }

    private fun nameRegex(part: String): Regex = Regex(
        part.map { char ->
            when (char) {
                '*' -> "[^/]*"
                '?' -> "[^/]"
                else -> Regex.escape(char.toString())
            }
        }.joinToString(""),
    )

    private companion object {
        const val ANY_DEPTH = "**"
    }
}
