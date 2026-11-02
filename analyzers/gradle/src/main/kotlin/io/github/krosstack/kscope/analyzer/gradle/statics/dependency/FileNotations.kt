package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

/**
 * A file that a `files(...)` notation names, or the directory of a `fileTree(...)` when [tree]: its [path] as
 * written, relative to the build root when [fromRoot] and to the project otherwise. A tree selects its files with
 * the [include] and [exclude] patterns it was written with.
 */
internal class DeclaredFile(
    val path: String,
    val fromRoot: Boolean,
    val tree: Boolean,
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
)

private val filesCall = Regex("""^files\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)
private val treeCall = Regex("""^fileTree\((.*?)\)(?:\s*\{(.*)})?$""", RegexOption.DOT_MATCHES_ALL)
private val quoted = Regex(""""(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'""")
private val directoryName = Regex("""(?<![\w.])["']?dir["']?\s*(?:to|:|=)""")
private val directoryKey = Regex("""["']?dir["']?\s*(?:to|:|=)\s*("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')""")
private val directoryPrefix = Regex("""^\$(?:\{([\w.]+)}|(\w+))/""")
private val rootDirectories = setOf("rootDir", "rootProject.rootDir", "rootProject.projectDir", "project.rootDir")
private val projectDirectories = setOf("projectDir", "project.projectDir")

/**
 * Reads the string literals of `files(...)` and the directory of `fileTree(...)`, in either DSL. A path may start
 * with the root or project directory; any other interpolation leaves it unread.
 */
internal object FileNotations {
    /** The files the notation names, or null when it is neither `files(...)` nor `fileTree(...)`. */
    fun parse(text: String): List<DeclaredFile>? {
        val files = filesCall.matchEntire(text)
        val tree = treeCall.matchEntire(text)
        return when {
            files != null -> quoted.findAll(files.groupValues[1]).mapNotNull { fileOf(it.value, tree = false) }.toList()
            tree != null -> listOfNotNull(treeOf(tree.groupValues[1], tree.groupValues[2]))
            else -> null
        }
    }

    private fun treeOf(arguments: String, block: String): DeclaredFile? {
        val patterns = TreeFilters.of(arguments, block)
        val directory = directoryOf(arguments)
        return if (patterns == null || directory == null) null else fileOf(directory, tree = true, patterns)
    }

    private fun directoryOf(arguments: String): String? {
        val named = directoryName.containsMatchIn(arguments)
        return if (named) directoryKey.find(arguments)?.groupValues?.get(1) else quoted.find(arguments)?.value
    }

    private fun fileOf(literal: String, tree: Boolean, patterns: TreePatterns? = null): DeclaredFile? {
        val text = literal.substring(1, literal.length - 1)
        val prefix = directoryPrefix.find(text)
        val directory = prefix?.groupValues?.drop(1)?.firstOrNull(String::isNotEmpty)
        val path = prefix?.let { text.substring(it.range.last + 1) } ?: text
        val readable = path.isNotEmpty() && '$' !in path && '\\' !in path &&
            (directory == null || directory in rootDirectories || directory in projectDirectories)
        val file = DeclaredFile(
            path,
            directory in rootDirectories,
            tree,
            patterns?.include.orEmpty(),
            patterns?.exclude.orEmpty(),
        )
        return file.takeIf { readable }
    }
}
