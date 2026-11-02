package io.github.krosstack.kscope.engine.structure

import java.nio.file.Path

internal object GitIgnoreMatches {
    private const val RECORD_FIELDS = 4
    private const val PATTERN_FIELD = 2
    private const val PATH_FIELD = 3
    private val globTokens = Regex("""\\.|(?<![^/])\*\*/|(?<![^/])\*\*$|\*|\?|\[(?:\\.|[^]])*]|.""")

    fun ignoredBelow(root: Path, checkoutDirectory: Path, output: ByteArray): Set<Path> {
        val matches = String(output, Charsets.UTF_8).split('\u0000').dropLast(1).chunked(RECORD_FIELDS)
            .filter { it.size == RECORD_FIELDS && !it[PATTERN_FIELD].startsWith('!') }
        if (matches.any { excludesAncestor(root, checkoutDirectory, it.first(), it[PATTERN_FIELD]) }) {
            return emptySet()
        }
        return matches.map { checkoutDirectory.resolve(it[PATH_FIELD]).normalize() }
            .filter { it != root && it.startsWith(root) }.toSet()
    }

    private fun excludesAncestor(root: Path, checkoutDirectory: Path, source: String, pattern: String): Boolean {
        val sourcePath = Path.of(source)
        val base = if (sourcePath.isAbsolute || sourcePath.startsWith(GitMarkerDetector.entryName())) {
            checkoutDirectory
        } else {
            checkoutDirectory.resolve(sourcePath).parent
        }
        if (pattern.isEmpty() || !root.startsWith(base)) return false
        val relative = base.relativize(root)
        val matcher = patternMatcher(pattern)
        val anchored = '/' in pattern.removeSuffix("/")
        return generateSequence(relative) { it.parent }.filter { it.toString().isNotEmpty() }.any {
            matcher.matches(if (anchored) it.joinToString("/") else it.fileName.toString())
        }
    }

    private fun patternMatcher(pattern: String): Regex {
        val glob = pattern.removePrefix("/").removeSuffix("/")
        val expression = globTokens.findAll(glob).joinToString("") { tokenExpression(it.value) }
        return Regex(expression)
    }

    private fun tokenExpression(token: String): String = when {
        token == "**/" -> "(?:[^/]+/)*"
        token == "**" -> ".*"
        token == "*" -> "[^/]*"
        token == "?" -> "[^/]"
        token.startsWith('\\') -> Regex.escape(token.drop(1))
        token.startsWith('[') -> token.replaceFirst("[!", "[^")
        else -> Regex.escape(token)
    }
}
