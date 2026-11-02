package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import io.github.krosstack.kscope.spi.worker.ScriptOutline

private val anywhere = TextRange(1, 1, 1, 1)

/** A block call such as `android { }`, with the statements inside it and the properties it assigns. */
internal fun block(name: String, vararg body: ScriptCall, assignments: Map<String, String> = emptyMap()) =
    ScriptCall(name, null, emptyList(), body.toList(), assignments, anywhere)

/** A call with one string literal argument, such as `id("acme.library")`. */
internal fun literalCall(name: String, literal: String) =
    ScriptCall(name, null, listOf(ScriptArgument(null, literal, "\"$literal\"")), emptyList(), emptyMap(), anywhere)

/** Adds a Kotlin script together with the outline [CheckedInOutlines] reads for it. */
internal fun StaticWorkspace.writeKotlinScript(path: String, vararg calls: ScriptCall) {
    write(path, "// outlined by the test\n")
    write("$path.outline.json", KscopeJson.encodeToString(ScriptOutline.serializer(), ScriptOutline(calls.toList())))
}
