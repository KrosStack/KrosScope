package io.github.krosstack.kscope.model

import kotlinx.serialization.Serializable

/** Normalized, `/`-separated and repository-relative. The empty path denotes the repository root. */
@Serializable
@JvmInline
public value class RepoPath(public val value: String) {
    init {
        require(value.isEmpty() || value.isNormalizedRelativePath()) {
            "A repository path must be normalized, '/'-separated and relative: '$value'"
        }
    }
}

private const val BACKSLASH = '\\'

private val windowsDrivePrefix = Regex("^[A-Za-z]:")

private fun String.isNormalizedRelativePath(): Boolean {
    val isRelative = !startsWith('/') && !windowsDrivePrefix.containsMatchIn(this)
    val hasOnlyNormalSegments = split('/').none { it.isEmpty() || it == "." || it == ".." }
    return BACKSLASH !in this && isRelative && hasOnlyNormalSegments
}
