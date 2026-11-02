package io.github.krosstack.kscope.engine.discovery

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Arrays

/**
 * The direct subdirectories of a root that hold a checkout: a `.git` entry that is a directory or a pointer file.
 * Hidden directories, symbolic links and the index directory are not candidates.
 */
internal class RepositoryCandidates(private val root: Path, private val indexDirectory: Path) {
    fun names(): List<String> = try {
        Files.newDirectoryStream(root).use { children ->
            children.filter(::isCandidate).map { it.fileName.toString() }.sortedWith(byteOrder)
        }
    } catch (_: IOException) {
        emptyList()
    }

    private fun isCandidate(directory: Path): Boolean {
        val name = directory.fileName.toString()
        return !name.startsWith(HIDDEN_PREFIX) &&
            Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) &&
            directory.toAbsolutePath().normalize() != indexDirectory &&
            hasGitEntry(directory)
    }

    private fun hasGitEntry(directory: Path): Boolean {
        val entry = directory.resolve(GIT_ENTRY)
        return Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) ||
            Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
    }

    private companion object {
        const val HIDDEN_PREFIX = "."
        const val GIT_ENTRY = ".git"

        val byteOrder = Comparator<String> { first, second ->
            Arrays.compareUnsigned(first.toByteArray(Charsets.UTF_8), second.toByteArray(Charsets.UTF_8))
        }
    }
}
