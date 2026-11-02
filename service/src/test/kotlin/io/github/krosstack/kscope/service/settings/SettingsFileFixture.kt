package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A text that must be refused, and the one error that is expected for it. */
internal class Rejection(val text: String, val line: Int, val key: String, val message: String)

internal val defaultScan = ScanReport(emptyList(), emptyList(), true, emptyList(), "not-indexed")

internal val unknownScan = defaultScan.copy(indexState = "unknown")

internal val defaultAnalyzers = AnalyzersReport(true, true, true, true, true, true, true, "not-indexed")

internal val unknownAnalyzers = defaultAnalyzers.copy(indexState = "unknown")

internal fun defaultReport(file: String?, resources: ResourcesReport) =
    SettingsReport(file, true, emptyList(), resources, defaultScan, defaultAnalyzers)

internal fun invalidReport(issue: SettingsIssue, resources: ResourcesReport) =
    SettingsReport("kscope.toml", false, listOf(issue), resources, unknownScan, unknownAnalyzers)

internal val notIndexed = IndexedScope.NOT_INDEXED

internal fun loaded(resources: ResourceSettings) = SettingsReading.Loaded(WorkspaceSettings(resources))

/** A directory that holds a `kscope.toml` which a test writes, and reads it the way a scan does. */
internal class SettingsFileFixture : AutoCloseable {
    val directory: Path = Files.createTempDirectory("kscope-settings-file")
    val file: Path = directory.resolve("kscope.toml")

    fun read(text: String): SettingsReading {
        Files.writeString(file, text)
        return readFile()
    }

    fun readFile(): SettingsReading = SettingsFile(directory).read()

    fun issuesOf(text: String): List<SettingsIssue> = assertIs<SettingsReading.Invalid>(read(text)).issues

    fun issuesOfFile(): List<SettingsIssue> = assertIs<SettingsReading.Invalid>(readFile()).issues

    fun assertRejected(rejection: Rejection) {
        val expected = SettingsIssue(rejection.line, rejection.key, rejection.message)
        assertEquals(listOf(expected), issuesOf(rejection.text), rejection.text)
    }

    override fun close() {
        TemporaryTree.delete(directory)
    }
}
