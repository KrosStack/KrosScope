package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.service.ScanLevel
import java.nio.file.Path

/** The resource settings of a workspace; null means that the settings do not set the value. */
public data class ResourceSettings(public val workers: Int? = null, public val workerHeapMib: Int? = null)

/** The scan settings of a workspace: which files a scan reads. The patterns follow `docs/cli.md`. */
public data class ScanSettings(
    public val include: List<String> = emptyList(),
    public val exclude: List<String> = emptyList(),
    public val tests: Boolean = true,
    public val generated: List<String> = emptyList(),
) {
    internal fun toScope(): ScanScope = ScanScope(include, exclude, tests, generated)
}

/**
 * What a workspace says about how it is scanned. [DEFAULTS] is what no settings file at all says; a caller that must
 * not depend on a file inside the workspace passes it to [io.github.krosstack.kscope.service.OpenOptions].
 */
public data class WorkspaceSettings(
    public val resources: ResourceSettings = ResourceSettings(),
    public val scan: ScanSettings = ScanSettings(),
    public val analyzers: AnalyzerSettings = AnalyzerSettings(),
    public val freshness: FreshnessSettings = FreshnessSettings(),
    public val level: ScanLevel? = null,
    public val components: Map<String, Boolean> = emptyMap(),
) {
    public companion object {
        public val DEFAULTS: WorkspaceSettings = WorkspaceSettings()

        public fun read(root: Path): WorkspaceSettings = when (val reading = SettingsFile(root).read()) {
            SettingsReading.Absent -> DEFAULTS
            is SettingsReading.Loaded -> reading.settings
            is SettingsReading.Invalid -> throw InvalidSettingsException("kscope.toml", reading.issues)
        }
    }
}
