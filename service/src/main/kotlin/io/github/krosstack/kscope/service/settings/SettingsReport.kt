package io.github.krosstack.kscope.service.settings

import kotlinx.serialization.Serializable

/**
 * One problem of a settings file. [line] and [key] are null when the problem concerns the file as a whole, such as a
 * symbolic link, and [key] is also null for a syntax error that names no key.
 */
@Serializable
public data class SettingsIssue(public val line: Int?, public val key: String?, public val message: String) {
    public fun describe(fileName: String): String {
        val place = if (line == null) fileName else "$fileName:$line"
        return if (key == null) "$place: $message" else "$place: $key: $message"
    }
}

/**
 * The value of one setting and where it comes from: `kscope.toml`, the name of an environment variable, or `default`.
 * [value] is null for a setting whose default is decided when a scan runs.
 */
@Serializable
public data class SettingValue(public val value: Int?, public val origin: String)

@Serializable
public data class ResourcesReport(public val workers: SettingValue, public val workerHeapMib: SettingValue)

/**
 * The scan settings as `info` shows them. [indexState] says whether the index was built with them: `current`,
 * `outdated` (scan again), `not-indexed`, or `unknown` while the settings file is not valid.
 */
@Serializable
public data class ScanReport(
    public val include: List<String>,
    public val exclude: List<String>,
    public val tests: Boolean,
    public val generated: List<String>,
    public val indexState: String,
)

/**
 * The analyzer switches as `info` shows them: whether each group is on. [indexState] says whether the index was built
 * with them, with the same values as [ScanReport.indexState].
 */
@Serializable
public data class AnalyzersReport(
    public val androidResources: Boolean,
    public val aidl: Boolean,
    public val schemas: Boolean,
    public val nativeBindings: Boolean,
    public val gitHistory: Boolean,
    public val composeResources: Boolean,
    public val webBundles: Boolean,
    public val indexState: String,
) {
    public fun disabled(): List<AnalyzerKind> = AnalyzerKind.entries.filterNot { enabled(it) }

    private fun enabled(kind: AnalyzerKind): Boolean = when (kind) {
        AnalyzerKind.ANDROID_RESOURCES -> androidResources
        AnalyzerKind.AIDL -> aidl
        AnalyzerKind.SCHEMAS -> schemas
        AnalyzerKind.NATIVE_BINDINGS -> nativeBindings
        AnalyzerKind.GIT_HISTORY -> gitHistory
        AnalyzerKind.COMPOSE_RESOURCES -> composeResources
        AnalyzerKind.WEB_BUNDLES -> webBundles
    }
}

/**
 * The level that the settings ask for and where it comes from: `kscope.toml`, `options` or `default`. [trusted] says
 * whether the user trusted this workspace on this machine, which is what lets a scan run the build for `exact`.
 */
@Serializable
public data class LevelReport(public val value: String, public val origin: String, public val trusted: Boolean)

/**
 * The settings of a workspace as `info` shows them. [file] is the name of the settings file that was found, or null
 * when there is none. When [valid] is false the file is ignored and [errors] say why a scan will refuse to start.
 */
@Serializable
public data class SettingsReport(
    public val file: String?,
    public val valid: Boolean,
    public val errors: List<SettingsIssue>,
    public val resources: ResourcesReport,
    public val scan: ScanReport,
    public val analyzers: AnalyzersReport,
    public val level: LevelReport = LevelReport("resolved", "default", trusted = false),
)
