package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.FileNotFound
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal object ClientPaths {
    private val options = setOf("--workspace", "--task-file")
    private val valueOptions = options + setOf(
        "--format",
        "--progress",
        "--detail",
        "--sections",
        "--as",
        "--budget",
        "--level",
        "--estimator",
        "--scope",
        "--node",
        "--depth",
        "--limit",
        "--structure-depth",
        "--idle-timeout",
        "--tasks",
        "--held-out",
        "--out",
    )

    fun resolve(arguments: List<String>, directory: Path, unescapeAt: Boolean = false): List<String> {
        var previous: String? = null
        var literal = false
        return arguments.map { argument ->
            val resolved = when {
                literal -> argument

                previous != null -> {
                    val value = if (previous in options) absolute(argument, directory) else argument
                    previous = null
                    value
                }

                argument == "--" -> {
                    literal = true
                    argument
                }

                argument in valueOptions -> {
                    previous = argument
                    argument
                }

                argument.substringBefore('=') in options && '=' in argument ->
                    argument.substringBefore('=') + "=" + absolute(argument.substringAfter('='), directory)

                else -> unescape(argument, unescapeAt)
            }
            resolved
        }
    }

    private fun unescape(argument: String, enabled: Boolean): String =
        if (enabled && argument.startsWith("@@")) argument.drop(1) else argument

    fun absolute(path: String, directory: Path): String = directory.resolve(path).normalize().toString()

    fun indexLocation(workspace: Path): String =
        workspace.toAbsolutePath().normalize().resolve(".kscope").toString().replace('\\', '/')

    fun readArgumentFile(file: String, directory: Path): String = try {
        Files.readString(directory.resolve(file), Charsets.UTF_8)
    } catch (_: IOException) {
        throw FileNotFound(file)
    }
}
