package io.github.krosstack.kscope.analyzer.gradle.script

import io.github.krosstack.kscope.spi.worker.ScriptCall

private val plainString = Regex("""^(?:"([^"\\$]*)"|'([^'\\]*)')$""")

/** The text of a string written without interpolation or escapes, or null for any other expression. */
internal fun unquote(raw: String): String? =
    plainString.matchEntire(raw.trim())?.groupValues?.let { it[1].ifEmpty { it[2] } }

/**
 * The raw text of a property of the block this call opens, however the DSL writes it: `name = value`,
 * `name(value)` or `name value`, or `name.set(value)`.
 */
internal fun ScriptCall.setting(name: String): String? {
    val property = assignments[name] ?: child(name)?.firstExpression()
    return property ?: body.firstOrNull { it.receiver == name && it.name == "set" }?.firstExpression()
}

internal fun ScriptCall.stringSetting(name: String): String? = setting(name)?.let(::unquote)

internal fun ScriptCall.flagSetting(name: String): Boolean? = when (setting(name)) {
    "true" -> true
    "false" -> false
    else -> null
}
