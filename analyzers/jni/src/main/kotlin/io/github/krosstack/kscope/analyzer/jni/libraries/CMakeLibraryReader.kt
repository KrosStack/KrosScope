package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.lexing.LineIndex
import io.github.krosstack.kscope.model.Certainty

private const val UNSPECIFIED = "unspecified"
private val skippedKeywords = setOf("IMPORTED", "ALIAS")
private val linkageByKeyword = mapOf("SHARED" to "shared", "MODULE" to "module")
private val otherTypes = setOf("STATIC", "OBJECT", "INTERFACE", "UNKNOWN")

/**
 * The libraries of `add_library` calls in `CMakeLists.txt`: those declared `SHARED` or `MODULE`, with a firm
 * certainty, and those that leave the type to `BUILD_SHARED_LIBS`, which are heuristic. `STATIC`, `OBJECT`,
 * `INTERFACE`, `UNKNOWN`, `IMPORTED` and `ALIAS` libraries are not built as shared libraries by this file, and a name
 * made of variables is not a name.
 */
internal class CMakeLibraryReader : LibraryTargetReader {
    override fun read(text: String): List<LibraryTarget> {
        val lines = LineIndex(text)
        return CMakeCommands(text).calls("add_library").mapNotNull { targetOf(it, lines) }
    }

    private fun targetOf(call: CMakeCall, lines: LineIndex): LibraryTarget? {
        val name = call.arguments.firstOrNull()?.takeUnless { '$' in it.value }
        val rest = call.arguments.drop(1).map { it.value }
        val isSkipped = rest.firstOrNull() in otherTypes || rest.any { it in skippedKeywords }
        return if (name == null || isSkipped) null else libraryOf(call, name, rest.firstOrNull(), lines)
    }

    private fun libraryOf(call: CMakeCall, name: CMakeArgument, type: String?, lines: LineIndex): LibraryTarget {
        val keyword = type?.takeIf { it in linkageByKeyword }
        return LibraryTarget(
            name = name.value,
            kind = LibraryKinds.CMAKE,
            linkage = linkageByKeyword[keyword] ?: UNSPECIFIED,
            certainty = if (keyword == null) Certainty.HEURISTIC else Certainty.EXACT,
            signature = listOfNotNull(name.value, keyword).joinToString(" ", "add_library(", ")"),
            nameRange = lines.rangeOf(name.start, name.end),
            declaration = lines.rangeOf(call.start, call.end),
        )
    }
}
