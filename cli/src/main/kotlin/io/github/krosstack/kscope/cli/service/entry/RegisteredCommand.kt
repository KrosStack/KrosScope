package io.github.krosstack.kscope.cli.service.entry

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class RegisteredCommand(val java: String, val argumentFile: String) {
    fun missing(): String? = when {
        isAbsent(java) -> "the Java executable $java"
        isAbsent(argumentFile) -> "the argument file $argumentFile"
        else -> missingClassPathEntry()?.let { "the class path entry $it" }
    }

    private fun missingClassPathEntry(): String? =
        LaunchFile(Path.of(argumentFile)).classPath().firstOrNull { isAbsent(it.removeSuffix("*")) }

    // A path that the operating system printed in another encoding cannot be told apart from a missing one.
    private fun isAbsent(path: String): Boolean = !path.contains(UNDECODABLE) && !exists(path)

    private fun exists(path: String): Boolean = try {
        Files.exists(Path.of(path))
    } catch (_: InvalidPathException) {
        false
    }

    companion object {
        private const val UNDECODABLE = '\uFFFD'

        fun of(words: List<String>): RegisteredCommand? =
            if (words.size == 2 && words[1].startsWith("@")) RegisteredCommand(words[0], words[1].drop(1)) else null
    }
}
