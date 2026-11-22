package io.github.krosstack.kscope.engine.discovery

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Reads from the files of Git where HEAD points, so that watching a repository starts no process. */
internal class GitHeadFiles(private val gitDirectory: Path) {
    private val commonDirectory = text(gitDirectory.resolve("commondir")).takeIf { it.isNotEmpty() }
        ?.let { gitDirectory.resolve(it).normalize() } ?: gitDirectory

    /** Differs after a commit, a checkout, a reset and any other move of HEAD, but not otherwise. */
    fun token(): String {
        val head = text(gitDirectory.resolve(HEAD))
        val reference = if (head.startsWith(REFERENCE_PREFIX)) {
            text(commonDirectory.resolve(head.removePrefix(REFERENCE_PREFIX)))
        } else {
            ""
        }
        return listOf(head, reference, metadata(PACKED), metadata("reftable/tables.list"))
            .joinToString(SEPARATOR)
    }

    fun commit(expired: () -> Boolean = { false }): String? {
        if (reftable()) return null
        val head = text(gitDirectory.resolve(HEAD))
        val name = head.removePrefix(REFERENCE_PREFIX)
        return when {
            name == head -> hash(head)
            name.startsWith(REFERENCES) && PARENT !in name -> referenced(name, expired)
            else -> null
        }
    }

    private fun reftable(): Boolean = listOf(commonDirectory, gitDirectory).any {
        Files.isDirectory(it.resolve(REFTABLE))
    } || refStorageConfigured()

    private fun refStorageConfigured(): Boolean = try {
        var inside = false
        Files.newBufferedReader(commonDirectory.resolve(CONFIG)).useLines { lines ->
            lines.any { raw ->
                val line = raw.trim().lowercase()
                if (line.startsWith("[")) {
                    inside = line.startsWith(EXTENSIONS)
                    inside && REF_STORAGE in line
                } else {
                    inside && line.startsWith(REF_STORAGE)
                }
            }
        }
    } catch (_: IOException) {
        false
    }

    private fun referenced(name: String, expired: () -> Boolean): String? {
        val loose = loose(name)
        return if (loose == null) packed(name, expired) else hash(loose)
    }

    private fun loose(name: String): String? = try {
        listOf(gitDirectory, commonDirectory).map { text(it.resolve(name)) }.firstOrNull { it.isNotEmpty() }
    } catch (_: InvalidPathException) {
        null
    }

    private fun packed(name: String, expired: () -> Boolean): String? = try {
        Files.newBufferedReader(commonDirectory.resolve(PACKED)).useLines { lines ->
            lines.takeWhile { !expired() }.firstOrNull { it.substringAfter(' ', "") == name }
                ?.let { hash(it.substringBefore(' ')) }
        }
    } catch (_: IOException) {
        null
    }

    private fun hash(text: String): String? =
        text.takeIf { it.length in HASH_LENGTHS && it.all { digit -> digit in HEX } }

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
        const val HEAD = "HEAD"
        const val PACKED = "packed-refs"
        const val REFTABLE = "reftable"
        const val CONFIG = "config"
        const val EXTENSIONS = "[extensions]"
        const val REF_STORAGE = "refstorage"
        const val REFERENCE_PREFIX = "ref: "
        const val REFERENCES = "refs/"
        const val PARENT = ".."
        const val HEX = "0123456789abcdef"
        const val SEPARATOR = "\n"
        const val MAX_BYTES = 1_024
        val HASH_LENGTHS = setOf(40, 64)
    }
}
