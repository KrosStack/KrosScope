package io.github.krosstack.kscope.engine.discovery

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal fun Path.toRepoPathString(root: Path): String = root.relativize(this).joinToString("/") { it.toString() }

/** Lists regular files below a root without following symbolic links, skipping the default excluded directories. */
internal object WorkspaceWalker {
    private val excludedDirectoryNames = setOf(".git", ".gradle", ".idea", ".kscope", "build", "out", "node_modules")

    /** Paths are relative to [root]; the walk starts at [start], which lies inside it and leaves out [skipping]. */
    fun relativeFiles(root: Path, start: Path = root, skipping: Set<Path> = emptySet()): List<String> {
        val found = mutableListOf<String>()
        Files.walkFileTree(
            start,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir != start && (isExcluded(dir) || dir in skipping)) {
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) found += file.toRepoPathString(root)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return found
    }

    private fun isExcluded(directory: Path) = directory.fileName.toString().lowercase() in excludedDirectoryNames
}
