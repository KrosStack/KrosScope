package io.github.krosstack.kscope.service.settings

/**
 * The freshness settings of a workspace read from their file now. When the file is not valid, [issues] say why and
 * [settings] are the defaults.
 */
public data class FreshnessReading(
    public val settings: FreshnessSettings = FreshnessSettings(),
    public val issues: List<SettingsIssue> = emptyList(),
) {
    public val valid: Boolean get() = issues.isEmpty()
}
