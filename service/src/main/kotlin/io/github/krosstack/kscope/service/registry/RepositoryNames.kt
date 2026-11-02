package io.github.krosstack.kscope.service.registry

import java.nio.file.Files
import java.nio.file.Path

/**
 * Names under which registered repositories print in a workspace. A name is a single directory name of letters,
 * digits, dots, hyphens and underscores that does not start with a dot, and no other entry of the workspace root or
 * other registered repository may have it.
 */
internal class RepositoryNames(private val root: Path, private val registered: Set<String>) {
    fun isValid(name: String): Boolean = name.length <= MAX_LENGTH && PATTERN.matches(name)

    fun isTaken(name: String): Boolean = name in registered || Files.exists(root.resolve(name))

    /** [base], made valid and unique by replacing other characters and appending `-2`, `-3` and so on. */
    fun unique(base: String): String {
        val cleaned = base.replace(INVALID, "-").trimStart('.', '-').take(MAX_LENGTH).ifEmpty { FALLBACK }
        if (!isTaken(cleaned)) return cleaned
        return generateSequence(2) { it + 1 }.map { "$cleaned-$it" }.first { !isTaken(it) }
    }

    companion object {
        /** The name a clone of [location] gets: the last segment of its path without `.git`. */
        fun ofLocation(location: String): String =
            location.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').substringAfterLast(':')
                .removeSuffix(".git")

        private const val MAX_LENGTH = 100
        private const val FALLBACK = "repository"
        private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val INVALID = Regex("[^A-Za-z0-9._-]")
    }
}
