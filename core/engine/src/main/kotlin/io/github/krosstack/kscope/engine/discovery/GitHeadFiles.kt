package io.github.krosstack.kscope.engine.discovery

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Reads from the files of Git where HEAD points, so that watching a repository starts no process. */
internal class GitHeadFiles(private val gitDirectory: Path) {
    private val commonDirectory = text(gitDirectory.resolve("commondir")).takeIf { it.isNotEmpty() }
        ?.let { gitDirectory.resolve(it).normalize() } ?: gitDirectory

    /** Differs after a commit, a checkout, a reset and any other move of HEAD, but not otherwise. */
    fun token(): String {
        val head = text(gitDirectory.resolve("HEAD"))
        val reference = if (head.startsWith(REFERENCE_PREFIX)) {
            text(commonDirectory.resolve(head.removePrefix(REFERENCE_PREFIX)))
        } else {
            ""
        }
        return listOf(head, reference, metadata("packed-refs"), metadata("reftable/tables.list"))
            .joinToString(SEPARATOR)
    }

    private fun metadata(name: String): String = try {
        Files.readAttributes(commonDirectory.resolve(name), BasicFileAttributes::class.java)
            .let { "${it.size()}/${it.lastModifiedTime()}" }
    } catch (_: IOException) {
        ""
    }

    private fun text(file: Path): String = try {
        if (Files.isRegularFile(file)) {
            Files.newInputStream(file).use { it.readNBytes(MAX_BYTES).toString(Charsets.UTF_8).trim() }
        } else {
            ""
        }
    } catch (_: IOException) {
        ""
    }

    private companion object {
        const val REFERENCE_PREFIX = "ref: "
        const val SEPARATOR = "\n"
        const val MAX_BYTES = 1_024
    }
}
