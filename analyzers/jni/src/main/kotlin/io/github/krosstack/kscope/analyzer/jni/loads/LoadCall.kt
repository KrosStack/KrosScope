package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.model.TextRange

/**
 * A call that loads a native library: the [library] it names, the [argument] as written, the [call] that loads it, the
 * place of the argument and the place of the whole call.
 */
internal data class LoadCall(
    val library: String,
    val argument: String,
    val call: String,
    val argumentRange: TextRange,
    val declaration: TextRange,
)
