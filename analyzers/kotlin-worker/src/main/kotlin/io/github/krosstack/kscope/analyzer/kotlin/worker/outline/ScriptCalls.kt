package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

private const val VALUE_CALL = "val"

/** The call that declares a local, a parameter or the extension receiver `this`, with its type and its value. */
internal fun valueBinding(name: String, type: String?, value: ScriptArgument?, range: TextRange): ScriptCall =
    ScriptCall(
        name = VALUE_CALL,
        receiver = null,
        arguments = listOf(ScriptArgument(null, name, name)) + listOfNotNull(value?.copy(name = null)),
        body = emptyList(),
        assignments = emptyMap(),
        range = range,
        typeArguments = listOfNotNull(type),
    )

/** The call and everything below it, placed at [range]. */
internal fun ScriptCall.placedAt(range: TextRange): ScriptCall =
    copy(range = range, body = body.map { it.placedAt(range) })

/** The call and everything below it, with the type [names] replaced. */
internal fun ScriptCall.withTypes(names: Map<String, String>): ScriptCall = if (names.isEmpty()) {
    this
} else {
    copy(
        typeArguments = typeArguments.map { names[it] ?: it },
        body = body.map { it.withTypes(names) },
    )
}
