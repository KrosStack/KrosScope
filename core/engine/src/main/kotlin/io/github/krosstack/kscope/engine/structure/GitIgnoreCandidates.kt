package io.github.krosstack.kscope.engine.structure

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * The paths whose ignore state Git is asked for: the directories and the marker files that the walk may meet. The
 * collection enters at most as many directories as the walk may; a path it did not reach has an unknown ignore state.
 */
internal class GitIgnoreCandidates private constructor(private val paths: Set<Path>, private val complete: Boolean) {
    fun reached(path: Path): Boolean = complete || path in paths

    /** The paths relative to [checkoutDirectory], each closed by a NUL, as `git check-ignore --stdin -z` reads them. */
    fun input(checkoutDirectory: Path): ByteArray = paths
        .joinToString("") { checkoutDirectory.relativize(it).joinToString("/").ifEmpty { "." } + NUL }
        .toByteArray(Charsets.UTF_8)

    companion object {
        private const val NUL = "\u0000"

        fun collect(root: Path, bounds: WalkBounds): GitIgnoreCandidates {
            val walk = CandidateWalk(root, bounds.maxDirectories)
            Files.walkFileTree(root, emptySet(), bounds.maxDepth + 1, walk)
            return GitIgnoreCandidates(walk.paths, !walk.truncated)
        }
    }
}

private class CandidateWalk(private val root: Path, private val limit: Int) : SimpleFileVisitor<Path>() {
    val paths = linkedSetOf<Path>()
    var truncated = false
    private var directories = 0

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = when {
        dir != root && SkippedDirectories.contains(dir.fileName.toString()) -> {
            paths.add(dir)
            FileVisitResult.SKIP_SUBTREE
        }

        directories >= limit -> {
            truncated = true
            FileVisitResult.TERMINATE
        }

        else -> {
            directories++
            if (dir != root) paths.add(dir)
            FileVisitResult.CONTINUE
        }
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (!attrs.isSymbolicLink && StructureMarkers.isMarker(file)) paths.add(file)
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
}

internal object StructureMarkers {
    fun isMarker(file: Path): Boolean {
        val name = file.fileName.toString()
        return GitMarkerDetector.isEntry(name) || ProjectMarkerDetector.isGradleSettings(name) ||
            ProjectMarkerDetector.otherEcosystem(name) != null
    }
}
