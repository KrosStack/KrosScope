package io.github.krosstack.kscope.engine.structure

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The directories that a Gradle settings script names with `includeBuild`, read from the text of the script. Only a
 * call in the code of the script counts: one inside a comment or inside a string literal names nothing.
 */
internal object IncludedBuilds {
    private val call = Regex("""(?<![A-Za-z0-9_$])includeBuild\s*\(?\s*$""")
    private const val MAX_BYTES = 65_536

    /** Paths as the report writes them, relative to [base]; the script is not run. */
    fun declaredIn(settings: Path, base: Path): List<String> {
        val directory = settings.parent
        val nestedComments = settings.fileName.toString().endsWith(".kts")
        return ScriptText.pieces(text(settings), nestedComments).zipWithNext()
            .filter { (code, literal) -> !code.literal && literal.literal && call.containsMatchIn(code.text) }
            .map { (_, literal) -> literal.text }.filter { it.isNotEmpty() }.distinct().sorted()
            .mapNotNull { resolved(directory, it) }.map { Paths.display(base, it) }
    }

    private fun resolved(directory: Path, name: String): Path? = try {
        directory.resolve(name).normalize()
    } catch (_: InvalidPathException) {
        null
    }

    private fun text(file: Path): String = try {
        Files.newInputStream(file).use { String(it.readNBytes(MAX_BYTES), Charsets.UTF_8) }
    } catch (_: IOException) {
        ""
    }
}

/** A stretch of a script: the content of one string literal, or the code up to the next literal without comments. */
internal class ScriptPiece(val text: String, val literal: Boolean)
