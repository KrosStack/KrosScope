package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.discovery.GitHeadFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

public object GitHeadCommit {
    public fun of(checkout: Path, expired: () -> Boolean = { false }): String? =
        generateSequence(checkout.toAbsolutePath().normalize()) { it.parent }
            .map { it.resolve(GitMarkerDetector.entryName()) }
            .firstOrNull { Files.exists(it) }
            ?.let(::directory)
            ?.let { GitHeadFiles(it).commit(expired) }

    private fun directory(entry: Path): Path? = try {
        if (Files.isDirectory(entry)) entry else pointed(entry)?.takeIf { Files.isDirectory(it) }
    } catch (_: IOException) {
        null
    } catch (_: InvalidPathException) {
        null
    }

    private fun pointed(entry: Path): Path? =
        Files.newBufferedReader(entry).use { it.readLine() }?.let(GitMarkerDetector::pointerTarget)
            ?.let { entry.resolveSibling(it).normalize() }
}
