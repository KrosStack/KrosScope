package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.lexing.LineIndex
import io.github.krosstack.kscope.model.Certainty

private val moduleAssignment = Regex("^\\s*LOCAL_MODULE\\s*(:=|\\+=|\\?=|=)\\s*(.*?)\\s*$")
private val includeOfBuildType = Regex("^\\s*-?include\\s+\\$[({]\\s*([A-Z_]+)\\s*[)}]\\s*$")
private const val SHARED_LIBRARY = "BUILD_SHARED_LIBRARY"

private const val APPEND = "+="
private const val ASSIGN_IF_UNSET = "?="

/**
 * The `LOCAL_MODULE` of a block, as the last assignment left it, waiting for the `include` that says what the
 * module is built as. A null name is a value that cannot be resolved to a single literal module name.
 */
private class PendingModule(val name: String?, val line: MakeLine)

/**
 * The libraries of an ndk-build `Android.mk`: the `LOCAL_MODULE` of each block that ends in
 * `include $(BUILD_SHARED_LIBRARY)`. A block ends at its first include of a build type, so static libraries,
 * executables and prebuilt modules are not libraries of this file, and a name made of variables is not a name.
 */
internal class NdkLibraryReader : LibraryTargetReader {
    override fun read(text: String): List<LibraryTarget> {
        val lines = LineIndex(text)
        val targets = mutableListOf<LibraryTarget>()
        var pending: PendingModule? = null
        MakeLines.of(text).forEach { line ->
            val buildType = includeOfBuildType.matchEntire(line.text)?.groupValues?.get(1)
            if (buildType == SHARED_LIBRARY) pending?.let { targetOf(it, lines) }?.let { targets += it }
            if (buildType != null) pending = null
            moduleAssignment.matchEntire(line.text)?.destructured?.let { (operator, value) ->
                pending = assigned(pending, operator, value, line)
            }
        }
        return targets
    }

    private fun assigned(current: PendingModule?, operator: String, value: String, line: MakeLine): PendingModule =
        when {
            operator == ASSIGN_IF_UNSET && current != null -> current
            operator == APPEND && current != null -> PendingModule(null, line)
            else -> PendingModule(literalNameOf(value), line)
        }

    private fun literalNameOf(value: String): String? =
        value.takeIf { it.isNotEmpty() && '$' !in it && it.none(Char::isWhitespace) }

    private fun targetOf(module: PendingModule, lines: LineIndex): LibraryTarget? {
        val name = module.name ?: return null
        val start = module.line.start + module.line.text.lastIndexOf(name)
        return LibraryTarget(
            name = name,
            kind = LibraryKinds.NDK,
            linkage = "shared",
            certainty = Certainty.EXACT,
            signature = "LOCAL_MODULE := $name",
            nameRange = lines.rangeOf(start, start + name.length),
            declaration = lines.rangeOf(module.line.start, module.line.end),
        )
    }
}
