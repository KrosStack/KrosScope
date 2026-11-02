package io.github.krosstack.kscope.engine.build.archive.folders

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The layout of the Gradle dependency cache: `files-2.1/group.with.dots/artifact/version/<hash>/artifact-version.jar`.
 * The folder can be that `files-2.1` directory, or hold it directly, below `modules-2`, or below `caches/modules-2`,
 * as the Gradle user home does. Of several hash directories, those in name order are tried one after the other.
 */
internal object GradleCacheLayout : FolderLayout {
    private const val FILES = "files-2.1"
    private const val MODULES = "modules-2"
    private const val CACHES = "caches"

    override fun archives(folder: Path, coordinate: MavenCoordinate): List<Path> =
        cachesIn(folder).flatMap { cache -> archivesIn(cache, coordinate) }

    private fun cachesIn(folder: Path): List<Path> {
        val itself = folder.takeIf { it.fileName?.toString() == FILES }
        val below = listOf(
            folder.resolve(FILES),
            folder.resolve(MODULES).resolve(FILES),
            folder.resolve(CACHES).resolve(MODULES).resolve(FILES),
        )
        return (listOfNotNull(itself) + below).filter(Files::isDirectory)
    }

    private fun archivesIn(cache: Path, coordinate: MavenCoordinate): List<Path> {
        val version = cache.resolve(coordinate.group).resolve(coordinate.artifact).resolve(coordinate.version)
        return hashDirectories(version).flatMap { hash ->
            listOf(coordinate.jarName, coordinate.aarName).map(hash::resolve).filter(Files::isRegularFile)
        }
    }

    private fun hashDirectories(version: Path): List<Path> = try {
        Files.list(version).use { entries -> entries.filter(Files::isDirectory).sorted().toList() }
    } catch (_: IOException) {
        emptyList()
    } catch (_: UncheckedIOException) {
        emptyList()
    }
}
