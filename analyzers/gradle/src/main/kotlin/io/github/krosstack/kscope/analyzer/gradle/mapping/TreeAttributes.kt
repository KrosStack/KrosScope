package io.github.krosstack.kscope.analyzer.gradle.mapping

/** The attributes of a dependency edge on a `fileTree(...)` that hold its `include` and `exclude` patterns. */
internal object TreeAttributes {
    private const val INCLUDE = "tree.include"
    private const val EXCLUDE = "tree.exclude"
    private const val SEPARATOR = "\n"

    fun of(include: List<String>, exclude: List<String>): Map<String, String> = listOfNotNull(
        include.takeIf { it.isNotEmpty() }?.let { INCLUDE to it.joinToString(SEPARATOR) },
        exclude.takeIf { it.isNotEmpty() }?.let { EXCLUDE to it.joinToString(SEPARATOR) },
    ).toMap()
}
