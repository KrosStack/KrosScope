package io.github.krosstack.kscope.engine.build.archive

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The JAR files below a directory, as paths relative to it that the [TreeFilter] keeps, in order. A symbolic link to
 * a file counts as that file; a link to a directory is not followed, so no cycle is walked. A directory that is
 * missing or cannot be read has none.
 */
internal object ArchiveTree {
    fun jarsIn(directory: Path, filter: TreeFilter): List<String> = try {
        val root = directory.toRealPath()
        Files.walk(root).use { entries ->
            entries.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).joinToString("/") }
                .filter { it.endsWith(JAR) && filter.keeps(it) }
                .sorted()
                .toList()
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: UncheckedIOException) {
        emptyList()
    }

    private const val JAR = ".jar"
}
