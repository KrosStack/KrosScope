package io.github.krosstack.kscope.analyzer.gradle.workspace

import io.github.krosstack.kscope.model.RepoPath
import java.nio.file.Path

/** Converts the absolute paths of the Gradle model to repository-relative paths, or rejects those outside. */
internal class WorkspacePaths(root: Path) {
    private val root: Path = root.toAbsolutePath().normalize()

    fun relative(absolute: String): RepoPath? {
        val path = Path.of(absolute).toAbsolutePath().normalize()
        if (!path.startsWith(root)) return null
        return RepoPath(root.relativize(path).toString().replace('\\', '/'))
    }

    fun isInside(absolute: String): Boolean = relative(absolute) != null

    /** The path relative to the repository, with `..` where it lies outside; null on another file system root. */
    fun reach(absolute: String): String? = try {
        root.relativize(Path.of(absolute).toAbsolutePath().normalize()).toString().replace('\\', '/')
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Replaces the location of the workspace in a message, so that stored diagnostics carry no absolute path. */
    fun scrub(message: String): String {
        val slashed = root.toString().replace('\\', '/')
        return message.replace(root.toString(), PLACEHOLDER).replace(slashed, PLACEHOLDER)
    }

    private companion object {
        const val PLACEHOLDER = "<workspace>"
    }
}
