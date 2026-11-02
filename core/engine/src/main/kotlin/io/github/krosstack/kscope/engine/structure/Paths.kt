package io.github.krosstack.kscope.engine.structure

import java.io.IOException
import java.nio.file.Path

internal object Paths {
    /** [path] as the report writes it: relative to [base] when that can be done, else absolute. */
    fun display(base: Path, path: Path): String {
        val absolute = path.toAbsolutePath().normalize()
        val relative = try {
            base.relativize(absolute).toString().replace('\\', '/')
        } catch (_: IllegalArgumentException) {
            absolute.toString().replace('\\', '/')
        }
        return relative.ifEmpty { "." }
    }

    /** A name for a directory that is the same for every way of reaching it. */
    fun identity(path: Path): String = try {
        path.toRealPath().toString().replace('\\', '/')
    } catch (_: IOException) {
        path.toAbsolutePath().normalize().toString().replace('\\', '/')
    }
}
