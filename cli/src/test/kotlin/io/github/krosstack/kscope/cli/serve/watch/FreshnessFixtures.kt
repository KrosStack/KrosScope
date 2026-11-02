package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.service.settings.FreshnessReading
import io.github.krosstack.kscope.service.settings.FreshnessSettings
import io.github.krosstack.kscope.service.settings.SettingsIssue

internal fun invalidFreshness() =
    FreshnessReading(issues = listOf(SettingsIssue(2, "freshness.quiet-seconds", "must be between 1 and 300")))

internal fun unwatchedFreshness() = FreshnessReading(FreshnessSettings(watch = false))

internal fun quietFreshness(seconds: Int) = FreshnessReading(FreshnessSettings(quietSeconds = seconds))
