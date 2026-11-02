package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.UsageError
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal fun taskInput(positional: String?, file: Path?): String {
    if ((positional == null) == (file == null)) {
        throw UsageError("Supply either a positional task or --task-file, but not both.")
    }
    return file?.let(::readTaskFile) ?: checkNotNull(positional)
}

private fun readTaskFile(file: Path): String = try {
    Files.readString(file, Charsets.UTF_8).removePrefix("\uFEFF")
} catch (failure: IOException) {
    throw InputFailure("The task file could not be read as UTF-8 (${failure.message}). Save it as UTF-8.", failure)
}
