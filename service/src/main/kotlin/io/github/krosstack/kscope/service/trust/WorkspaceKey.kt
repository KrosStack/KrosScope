package io.github.krosstack.kscope.service.trust

import java.io.File
import java.io.IOException
import java.nio.file.Path

/** The canonical path of a workspace, the one name under which the machine knows it however it is reached. */
internal object WorkspaceKey {
    fun of(workspace: Path): String = real(workspace.toAbsolutePath()).toString().replace(File.separatorChar, '/')

    private fun real(absolute: Path): Path = try {
        absolute.toRealPath()
    } catch (_: IOException) {
        nearestReal(absolute.normalize())
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
