package io.github.krosstack.kscope.engine.build.archive.folders

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The folders that the user registered with the workspace to read built archives from, such as a Maven local
 * repository or a Gradle cache, in the order they were registered. Nothing is read from them unless a dependency asks
 * for a coordinate; a folder that is missing holds nothing.
 */
internal class ArtifactFolders(private val folders: List<Path>) {
    val isEmpty: Boolean get() = folders.isEmpty()

    /** The first JAR or AAR of [coordinate] in the first folder that holds one, in Maven and then Gradle layout. */
    fun find(coordinate: MavenCoordinate): FoundArchive? = folders.asSequence()
        .flatMap { folder -> layouts.asSequence().flatMap { it.archives(folder, coordinate).asSequence() } }
        .firstNotNullOfOrNull(::describe)

    /** The folders as the parts of a fingerprint, so that registering or removing one changes it. */
    fun fingerprint(): List<String> = folders.map { "folder ${it.toString().replace(File.separatorChar, '/')}" }

    private fun describe(archive: Path): FoundArchive? = try {
        FoundArchive(archive, Files.size(archive), Files.getLastModifiedTime(archive).toMillis())
    } catch (_: IOException) {
        null
    }

    private companion object {
        val layouts = listOf(MavenLayout, GradleCacheLayout)
    }
}
