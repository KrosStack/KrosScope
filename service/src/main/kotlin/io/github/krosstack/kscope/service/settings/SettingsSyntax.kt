package io.github.krosstack.kscope.service.settings

import org.tomlj.TomlParseResult

/** The syntax errors of a TOML document, as issues. A duplicate key names its key. */
internal object SettingsSyntax {
    private val DUPLICATE = Regex("(.+) previously defined at line \\d+, column \\d+")

    fun issuesOf(toml: TomlParseResult): List<SettingsIssue> = toml.errors().map { error ->
        val message = error.message.orEmpty()
        SettingsIssue(error.position().line(), DUPLICATE.matchEntire(message)?.groupValues?.get(1), message)
    }
}
