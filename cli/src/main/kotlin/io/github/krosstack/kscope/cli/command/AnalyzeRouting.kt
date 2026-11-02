package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.resolve
import java.nio.file.Files
import java.nio.file.Path

/** What `analyze` can make of its argument: a task to rank files for, or a name or file to describe. */
internal enum class Reading { TASK, SYMBOL }

/** The reading chosen for an argument, and the text that the lookup then uses. */
internal class Routing(val reading: Reading, val argument: String, val requested: Boolean)

/**
 * Decides what `analyze` does with its argument. A single token that looks like an identifier is a name to look up:
 * CamelCase, snake_case, a dotted qualified name, or a file that exists in the workspace, with an optional `:line`.
 * A plain lowercase word and anything with spaces is a task. `--as` overrides the guess.
 */
internal object IntentRouting {
    private val LINE = Regex(":[0-9]+$")

    fun route(text: String, requested: Reading?, workspace: Path): Routing {
        val token = text.trim()
        val file = workspaceRelativeFile(token, workspace)
        return when {
            requested != null -> Routing(requested, file ?: token, requested = true)
            file != null -> Routing(Reading.SYMBOL, file, requested = false)
            looksLikeName(token) -> Routing(Reading.SYMBOL, token, requested = false)
            else -> Routing(Reading.TASK, token, requested = false)
        }
    }

    private fun looksLikeName(token: String): Boolean =
        token.none(Char::isWhitespace) && (isCasedIdentifier(token) || isQualifiedName(token))

    private fun isCasedIdentifier(token: String): Boolean =
        isIdentifier(token) && token.codePoints().anyMatch(Character::isLetter) &&
            token.codePoints().anyMatch { it == '_'.code || Character.isUpperCase(it) }

    private fun isQualifiedName(token: String): Boolean {
        val names = token.split('.')
        return names.size > 1 && names.all(::isIdentifier)
    }

    private fun isIdentifier(token: String): Boolean {
        val points = token.codePoints().toArray()
        if (points.isEmpty()) return false
        val first = points.first()
        return (first == '_'.code || Character.isUnicodeIdentifierStart(first)) &&
            points.drop(1).all { Character.isUnicodeIdentifierPart(it) && !Character.isIdentifierIgnorable(it) }
    }

    private fun workspaceRelativeFile(token: String, workspace: Path): String? {
        val path = token.replace(LINE, "")
        val root = workspace.toAbsolutePath().normalize()
        val file = runCatching { root.resolve(path).normalize() }.getOrNull()
        val inside = file != null && file.startsWith(root) && Files.isRegularFile(file)
        val relative = file?.takeIf { inside }?.let { root.relativize(it).toString().replace('\\', '/') }
        return relative?.plus(token.substring(path.length))
    }
}
