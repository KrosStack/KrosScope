package io.github.krosstack.kscope.engine.build.archive

/**
 * Which files of a `fileTree(...)` count, from the `include` and `exclude` patterns the dependency carries. A file is
 * kept when it matches some include, or there is none, and neither it nor a directory above it matches an exclude.
 */
internal class TreeFilter private constructor(
    private val includes: List<TreePattern>,
    private val excludes: List<TreePattern>,
) {
    fun keeps(relativePath: String): Boolean {
        val names = relativePath.split('/')
        val included = includes.isEmpty() || includes.any { it.matches(names) }
        return included && excludes.none { exclude -> (1..names.size).any { exclude.matches(names.take(it)) } }
    }

    companion object {
        private const val INCLUDE = "tree.include"
        private const val EXCLUDE = "tree.exclude"
        private const val SEPARATOR = '\n'

        fun of(attributes: Map<String, String>): TreeFilter =
            TreeFilter(patternsOf(attributes[INCLUDE]), patternsOf(attributes[EXCLUDE]))

        private fun patternsOf(joined: String?): List<TreePattern> =
            joined?.split(SEPARATOR)?.map(::TreePattern).orEmpty()
    }
}
