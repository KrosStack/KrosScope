package io.github.krosstack.kscope.analyzer.gradle.workspace

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Finds the directory of the workspace from where one of its artifacts is on disk. */
internal object WorkspaceRoot {
    fun of(files: WorkspaceFiles, artifact: Artifact): Path {
        val absolute = files.absolutePath(artifact.ref)
        val depth = artifact.ref.path.value.split('/').size
        return generateSequence(absolute) { it.parent }.drop(depth).first()
    }
}

/**
 * What is on disk below the workspace, asked by repository-relative path. Names match exactly, whatever the case
 * rules of the file system, so that the same workspace gives the same answers everywhere.
 */
internal class WorkspaceDisk(private val root: Path) {
    private val listings = HashMap<String, List<String>>()

    fun isDirectory(path: String): Boolean = exists(path) && Files.isDirectory(resolve(path))

    fun isFile(path: String): Boolean = exists(path) && Files.isRegularFile(resolve(path))

    fun children(path: String): List<String> = listings.getOrPut(path) {
        try {
            Files.list(resolve(path)).use { entries -> entries.map { it.fileName.toString() }.sorted().toList() }
        } catch (ignored: IOException) {
            emptyList()
        }
    }

    /** The text of a file, or null if it cannot be read. */
    fun readText(path: String): String? = try {
        if (isFile(path)) Files.readString(resolve(path)) else null
    } catch (ignored: IOException) {
        null
    }

    private fun exists(path: String): Boolean =
        path.isEmpty() || path.substringAfterLast('/') in children(RepoPaths.parentOf(path))

    private fun resolve(path: String): Path = if (path.isEmpty()) root else root.resolve(path)
}
