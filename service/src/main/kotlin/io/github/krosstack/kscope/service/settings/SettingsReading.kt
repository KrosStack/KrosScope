package io.github.krosstack.kscope.service.settings

internal sealed interface SettingsReading {
    data object Absent : SettingsReading

    data class Loaded(val settings: WorkspaceSettings) : SettingsReading

    data class Invalid(val issues: List<SettingsIssue>) : SettingsReading
}
