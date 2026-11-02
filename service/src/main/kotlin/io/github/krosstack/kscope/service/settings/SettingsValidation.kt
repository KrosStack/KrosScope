package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.ScanLevel
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

/** Checks a TOML document against [SettingsGrammar]: unknown keys, types and ranges. Nothing is left unchecked. */
internal class SettingsValidation(private val toml: TomlParseResult) {
    private val present: Set<List<String>> = toml.keyPathSet(true)

    fun issues(): List<SettingsIssue> {
        val scope = SettingsScopeValidation(toml, present).issues()
        return (unknownKeys() + wrongIntegers() + wrongTables() + wrongLevel() + scope)
            .sortedWith(compareBy<SettingsIssue>({ it.line }, { it.key }))
    }

    private fun unknownKeys(): List<SettingsIssue> {
        val unknown = present.filter { it !in SettingsGrammar.KNOWN }
        return unknown.filterNot { isInsideReported(it, unknown) }.map { issueAt(it, unknownMessage(it)) }
    }

    private fun isInsideReported(path: List<String>, unknown: List<List<String>>): Boolean =
        (1 until path.size).any { path.take(it) in unknown || path.take(it) in SettingsGrammar.VALUES }

    private fun unknownMessage(path: List<String>): String {
        val siblings = SettingsGrammar.KNOWN.filter { it.size == path.size && it.dropLast(1) == path.dropLast(1) }
        return "unknown key (valid keys: ${siblings.joinToString(", ") { it.last() }})"
    }

    private fun wrongIntegers(): List<SettingsIssue> = SettingsGrammar.INTEGERS.mapNotNull { (path, range) ->
        when (val value = valueAt(path)) {
            null -> null
            !is Long -> issueAt(path, "must be an integer")
            else -> if (value in range) null else issueAt(path, rangeMessage(range))
        }
    }

    private fun wrongTables(): List<SettingsIssue> = SettingsGrammar.TABLES.mapNotNull { path ->
        valueAt(path)?.takeIf { it !is TomlTable }?.let { issueAt(path, "must be a table") }
    }

    private fun wrongLevel(): List<SettingsIssue> {
        val value = valueAt(SettingsGrammar.LEVEL)
        val message = when {
            value == null -> null
            value !is String -> LEVEL_NOT_A_STRING
            ScanLevel.parse(value) == null -> "must be one of the levels ($LEVEL_CHOICES)"
            else -> null
        }
        return listOfNotNull(message?.let { issueAt(SettingsGrammar.LEVEL, it) })
    }

    private fun valueAt(path: List<String>): Any? = if (path in present) toml.get(path) else null

    private fun rangeMessage(range: IntRange): String =
        if (range.first == range.last) "must be ${range.first}" else "must be between ${range.first} and ${range.last}"

    private fun issueAt(path: List<String>, message: String) =
        SettingsIssue(toml.inputPositionOf(path)?.line(), path.joinToString("."), message)

    private companion object {
        const val LEVEL_NOT_A_STRING = "must be a string"
        val LEVEL_CHOICES = "valid values: " + ScanLevel.wireNames.joinToString(", ")
    }
}
