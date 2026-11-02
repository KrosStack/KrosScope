package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.ScanLevel

/** Assembles the report of `info` from settings, the problems found in their file and what the index was built with. */
internal class SettingsReporting(private val resolution: ResourceResolution, private val trusted: () -> Boolean) {
    fun report(
        file: String?,
        issues: List<SettingsIssue>,
        values: WorkspaceSettings,
        origin: String,
        index: IndexedScope,
    ): SettingsReport {
        val valid = issues.isEmpty()
        return SettingsReport(
            file,
            valid,
            issues,
            resolution.resolve(values.resources, origin),
            scanReportOf(values.scan, if (valid) index.stateOf(values.scan.toScope()) else UNKNOWN_STATE),
            analyzersReportOf(values.analyzers, if (valid) index.stateOf(values.analyzers) else UNKNOWN_STATE),
            levelReportOf(values.level, origin),
        )
    }

    private fun levelReportOf(level: ScanLevel?, origin: String) = LevelReport(
        value = (level ?: ScanLevel.RESOLVED).wireName,
        origin = if (level == null) "default" else origin,
        trusted = trusted(),
    )

    private fun scanReportOf(scan: ScanSettings, indexState: String) =
        ScanReport(scan.include, scan.exclude, scan.tests, scan.generated, indexState)

    private fun analyzersReportOf(analyzers: AnalyzerSettings, indexState: String) = AnalyzersReport(
        androidResources = analyzers.isEnabled(AnalyzerKind.ANDROID_RESOURCES),
        aidl = analyzers.isEnabled(AnalyzerKind.AIDL),
        schemas = analyzers.isEnabled(AnalyzerKind.SCHEMAS),
        nativeBindings = analyzers.isEnabled(AnalyzerKind.NATIVE_BINDINGS),
        gitHistory = analyzers.isEnabled(AnalyzerKind.GIT_HISTORY),
        composeResources = analyzers.isEnabled(AnalyzerKind.COMPOSE_RESOURCES),
        webBundles = analyzers.isEnabled(AnalyzerKind.WEB_BUNDLES),
        indexState = indexState,
    )

    private companion object {
        const val UNKNOWN_STATE = "unknown"
    }
}
