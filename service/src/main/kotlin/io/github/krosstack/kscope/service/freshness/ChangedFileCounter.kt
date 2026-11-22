package io.github.krosstack.kscope.service.freshness

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant

internal class ChangedFileCounter(
    private val root: Path,
    private val since: Instant,
    private val deadline: Deadline,
    private val maxFiles: Int,
) : SimpleFileVisitor<Path>() {
    private var visited = 0
    var changed = 0
        private set
    var late = false
        private set
    var tooMany = false
        private set

    override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
        late = deadline.expired()
        return when {
            late -> FileVisitResult.TERMINATE
            directory != root && directory.fileName?.toString() in SKIPPED -> FileVisitResult.SKIP_SUBTREE
            else -> FileVisitResult.CONTINUE
        }
    }

    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
        late = deadline.expired()
        tooMany = !late && ++visited > maxFiles
        if (late || tooMany) return FileVisitResult.TERMINATE
        if (attributes.lastModifiedTime().toInstant() > since) changed++
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, failure: IOException): FileVisitResult = FileVisitResult.CONTINUE

    private companion object {
        val SKIPPED = setOf(".git", ".gradle", ".idea", ".kscope", "build", "out", "node_modules")
    }
}
