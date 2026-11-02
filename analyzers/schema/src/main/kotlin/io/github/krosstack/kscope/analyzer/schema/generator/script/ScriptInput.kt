package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.Span

/** Where a path of a build script starts: the directory of the build root, the directory of the module or neither. */
internal enum class PathAnchor { BUILD_ROOT, MODULE, PLAIN }

/**
 * A path as a build script writes it. [text] is the literal without its quotes, [anchor] is what its prefix names and
 * [relative] is what follows the prefix, without the slash after it.
 */
internal class ScriptPath(val text: String, val anchor: PathAnchor, val relative: String) {
    val name: String get() = text.trimEnd('/').substringAfterLast('/')
}

/**
 * An input of a code generator that a build script gives: the [path] it names, the span of the [call] that gives it
 * and of the [name] the call is made with. An OpenAPI input also carries the [openApi] settings of its block.
 */
internal class ScriptInput(
    val kind: InputKind,
    val path: ScriptPath,
    val call: Span,
    val name: Span,
    val openApi: OpenApiSettings? = null,
)
