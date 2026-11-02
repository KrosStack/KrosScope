package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.hashing.digestOf
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * What a classpath entry looks like on disk, as Gradle's up-to-date checks see it: the size and modification time of a
 * file, or of each file below a directory, or that there is nothing. An entry is looked at once for the life of this
 * object, which is one scan.
 */
internal class ClasspathContent {
    private val states = HashMap<String, String>()

    fun of(entry: String): String = states.getOrPut(entry) { stateOf(entry) }

    private fun stateOf(entry: String): String = try {
        stateOf(Path.of(entry))
    } catch (_: InvalidPathException) {
        ABSENT
    } catch (_: IOException) {
        UNREADABLE
    } catch (_: UncheckedIOException) {
        UNREADABLE
    }

    private fun stateOf(path: Path): String = when {
        Files.isRegularFile(path) -> fileState(path)
        Files.isDirectory(path) -> treeState(path)
        else -> ABSENT
    }

    private fun treeState(directory: Path): String = Files.walk(directory).use { paths ->
        val files = paths.filter { Files.isRegularFile(it) }.map { directory.relativize(it).joinToString("/") }
            .sorted().toList()
        digestOf(files.flatMap { listOf(it, fileState(directory.resolve(it))) })
    }

    private fun fileState(file: Path): String {
        val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
        return "${attributes.size()}:${attributes.lastModifiedTime()}"
    }

    private companion object {
        const val ABSENT = "absent"
        const val UNREADABLE = "unreadable"
    }
}
