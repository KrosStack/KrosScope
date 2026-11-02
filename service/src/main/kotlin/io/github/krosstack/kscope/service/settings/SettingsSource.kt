package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.trust.TrustStore
import java.nio.file.Path

/**
 * Where a workspace gets its settings: the [explicit] ones when the caller passed any, else the file in [root], which
 * is read when a scan starts or `info` is asked, and never by a query. The file is read afresh every time, so an edit
 * applies to the next scan without reopening the workspace.
 */
internal class SettingsSource(
    private val root: Path,
    private val explicit: WorkspaceSettings?,
    environment: (String) -> String?,
    private val trust: TrustStore = TrustStore.ofThisMachine(environment = environment),
) {
    private val reporting = SettingsReporting(ResourceResolution(environment), ::isTrusted)

    @Volatile
    private var current: WorkerSettings = explicit?.let(::workerSettingsOf) ?: WorkerSettings.NONE

    @Volatile
    private var prepared: WorkspaceSettings? = explicit

    /** The worker values of the scan that runs now; the Kotlin workers ask for them whenever they start. */
    fun workerSettings(): WorkerSettings = current

    /**
     * The files the scan that runs now reads. Before the first scan it is what the file says when the file is valid,
     * so that a watcher looks at the files the next scan will read.
     */
    fun scope(): ScanScope = (prepared ?: lenient()).scan.toScope()

    /** The analyzer groups the scan that runs now uses, known in the same way as [scope]. */
    fun analyzers(): AnalyzerSettings = (prepared ?: lenient()).analyzers

    /** The level that the settings of the scan that runs now ask for; null when they say nothing. */
    fun requestedLevel(): ScanLevel? = (prepared ?: lenient()).level

    /** Whether the user trusted this workspace on this machine to run its own build. */
    fun isTrusted(): Boolean = trust.isTrusted(root)

    /** Takes the settings for a scan. An invalid file fails here, before the index is touched. */
    fun prepareScan() {
        val settings = explicit ?: fromFile()
        current = workerSettingsOf(settings)
        prepared = settings
    }

    fun report(index: IndexedScope): SettingsReport = if (explicit != null) {
        reporting.report(null, emptyList(), explicit, EXPLICIT_ORIGIN, index)
    } else {
        reportOfFile(index)
    }

    private fun lenient(): WorkspaceSettings =
        (SettingsFile(root).read() as? SettingsReading.Loaded)?.settings ?: WorkspaceSettings.DEFAULTS

    private fun fromFile(): WorkspaceSettings = when (val reading = SettingsFile(root).read()) {
        SettingsReading.Absent -> WorkspaceSettings.DEFAULTS
        is SettingsReading.Loaded -> reading.settings
        is SettingsReading.Invalid -> throw InvalidSettingsException(FILE_NAME, reading.issues)
    }

    private fun reportOfFile(index: IndexedScope): SettingsReport = when (val reading = SettingsFile(root).read()) {
        SettingsReading.Absent -> reporting.report(null, emptyList(), WorkspaceSettings.DEFAULTS, FILE_NAME, index)

        is SettingsReading.Loaded -> reporting.report(FILE_NAME, emptyList(), reading.settings, FILE_NAME, index)

        is SettingsReading.Invalid ->
            reporting.report(FILE_NAME, reading.issues, WorkspaceSettings.DEFAULTS, FILE_NAME, index)
    }

    private fun workerSettingsOf(settings: WorkspaceSettings) =
        WorkerSettings(settings.resources.workers, settings.resources.workerHeapMib)

    private companion object {
        const val FILE_NAME = WorkspaceConfig.SETTINGS_FILE_NAME
        const val EXPLICIT_ORIGIN = "options"
    }
}
