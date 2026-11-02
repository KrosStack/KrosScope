package io.github.krosstack.kscope.spi.worker

import io.github.krosstack.kscope.model.TextRange
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Generic syntactic outline of a build script: nested calls with literal arguments, and assignments. The outline of
 * a Kotlin source file has no [calls]; it lists the plugin classes the file declares in [pluginClasses].
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class ScriptOutline(
    public val calls: List<ScriptCall>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val pluginClasses: List<PluginClassOutline> = emptyList(),
)

/**
 * One call of a script or of a function body. [typeArguments] are the type arguments written at the call, or, for the
 * `val` call that declares a local, the declared type. A call with [inlined] set stands for a function of the same
 * source set: its arguments are those of the call, and its [body] is the body of the function, opening with one `val`
 * call for each parameter (named after the parameter, valued by the argument that was passed, typed as declared) and
 * for the extension receiver (named `this`).
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class ScriptCall(
    public val name: String,
    public val receiver: String?,
    public val arguments: List<ScriptArgument>,
    public val body: List<ScriptCall>,
    public val assignments: Map<String, String>,
    public val range: TextRange,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val typeArguments: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val inlined: Boolean = false,
)

@Serializable
public data class ScriptArgument(public val name: String?, public val literal: String?, public val expression: String)

/**
 * A class that implements `Plugin<...>`, as far as the syntax of its source file shows: its qualified [name] and the
 * statements of its `apply` function, whose single parameter opens [apply] as a `val` call. [notInlined] names the
 * functions of the same source set that [apply] calls and that were not inlined: beyond the depth limit, recursive,
 * ambiguous, or given a lambda.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class PluginClassOutline(
    public val name: String,
    public val apply: List<ScriptCall>,
    public val range: TextRange,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val notInlined: List<String> = emptyList(),
)
