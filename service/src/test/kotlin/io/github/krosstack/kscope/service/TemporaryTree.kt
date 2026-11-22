package io.github.krosstack.kscope.service

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal object TemporaryTree : SimpleFileVisitor<Path>() {
    fun delete(root: Path) {
        Files.walkFileTree(root, this)
    }

    fun deleteEventually(root: Path) {
        repeat(ATTEMPTS) {
            if (deleted(root)) return
            Thread.sleep(PAUSE_MILLIS)
        }
        delete(root)
    }

    private fun deleted(root: Path): Boolean = try {
        delete(root)
        !Files.exists(root)
    } catch (_: IOException) {
        false
    }

    private const val ATTEMPTS = 50
    private const val PAUSE_MILLIS = 100L

    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
        file.toFile().setWritable(true)
        Files.deleteIfExists(file)
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, error: IOException): FileVisitResult = try {
        throw error
    } catch (_: NoSuchFileException) {
        FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
        if (error != null) return visitFileFailed(directory, error)
        Files.deleteIfExists(directory)
        return FileVisitResult.CONTINUE
    }
}
