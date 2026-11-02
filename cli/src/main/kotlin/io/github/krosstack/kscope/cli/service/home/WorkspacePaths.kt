package io.github.krosstack.kscope.cli.service.home

import java.io.File
import java.io.IOException
import java.nio.file.Path

internal object WorkspacePaths {
    fun key(path: Path): String = path.toString().replace(File.separatorChar, '/')

    fun real(path: Path): Path {
        val absolute = path.toAbsolutePath()
        return try {
            absolute.toRealPath()
        } catch (_: IOException) {
            nearestReal(absolute.normalize())
        }
    }

    private fun nearestReal(absolute: Path): Path =
        generateSequence(absolute) { it.parent }.firstNotNullOfOrNull { ancestor ->
            try {
                ancestor.toRealPath().resolve(ancestor.relativize(absolute))
            } catch (_: IOException) {
                null
            }
        } ?: absolute
}
