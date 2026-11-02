package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SettingsSourceTest {
    private val root: Path = Files.createTempDirectory("kscope-settings-source")
    private val none = ResourcesReport(SettingValue(null, "default"), SettingValue(2048, "default"))

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(root)
    }

    private fun source(explicit: WorkspaceSettings? = null, environment: Map<String, String> = emptyMap()) =
        SettingsSource(root, explicit, environment::get)

    private fun write(text: String) {
        Files.writeString(root.resolve("kscope.toml"), text)
    }

    @Test
    fun `a workspace without a file has no file to report and starts every worker as before`() {
        val source = source()

        source.prepareScan()

        assertEquals(defaultReport(null, none), source.report(notIndexed))
        assertNull(source.workerSettings().workers)
        assertNull(source.workerSettings().heapMebibytes)
    }

    @Test
    fun `a valid file is reported with its values and handed to the workers by a scan`() {
        write("[resources]\nworkers = 2\nworker-heap-mib = 512\n")
        val source = source()

        assertNull(source.workerSettings().workers, "nothing is read before a scan starts")
        source.prepareScan()

        val resources = ResourcesReport(SettingValue(2, "kscope.toml"), SettingValue(512, "kscope.toml"))
        val expected = defaultReport("kscope.toml", resources)
        assertEquals(expected, source.report(notIndexed))
        assertEquals(2, source.workerSettings().workers)
        assertEquals(512, source.workerSettings().heapMebibytes)
    }

    @Test
    fun `an invalid file fails the scan, is reported, and its values are not used`() {
        write("[resources]\nworkers = 2\nworker-heap-mib = 1\n")
        val source = source()

        val failure = assertFailsWith<InvalidSettingsException> { source.prepareScan() }

        val issue = SettingsIssue(3, "resources.worker-heap-mib", "must be between 256 and 8192")
        assertEquals(listOf(issue), failure.issues)
        assertEquals(
            "kscope.toml is not valid, so the scan did not start:\n" +
                "  kscope.toml:3: resources.worker-heap-mib: must be between 256 and 8192",
            failure.message,
        )
        assertEquals(invalidReport(issue, none), source.report(notIndexed))
        assertNull(source.workerSettings().workers)
    }

    @Test
    fun `the file is read again for every scan`() {
        val source = source()
        write("[resources]\nworkers = 2\n")
        source.prepareScan()
        assertEquals(2, source.workerSettings().workers)

        write("[resources]\nworkers = 5\n")
        source.prepareScan()
        assertEquals(5, source.workerSettings().workers)

        Files.delete(root.resolve("kscope.toml"))
        source.prepareScan()
        assertNull(source.workerSettings().workers)
    }

    @Test
    fun `a scan that is refused leaves the values of the scan before it`() {
        val source = source()
        write("[resources]\nworkers = 2\n")
        source.prepareScan()

        write("[resources]\nworkers = 99\n")
        assertFailsWith<InvalidSettingsException> { source.prepareScan() }

        assertEquals(2, source.workerSettings().workers)
    }

    @Test
    fun `settings that the caller passed are used and the file is never read`() {
        write("this is not toml")
        val explicit = WorkspaceSettings(ResourceSettings(workers = 3))
        val source = source(explicit)

        assertEquals(3, source.workerSettings().workers)
        source.prepareScan()

        val resources = ResourcesReport(SettingValue(3, "options"), SettingValue(2048, "default"))
        assertEquals(defaultReport(null, resources), source.report(notIndexed))
    }

    @Test
    fun `the fixed defaults are what a benchmark passes and ignore an invalid file`() {
        write("[freshness]\nwatch = true\n")
        val source = source(WorkspaceSettings.DEFAULTS)

        source.prepareScan()

        assertEquals(defaultReport(null, none), source.report(notIndexed))
        assertNull(source.workerSettings().workers)
    }
}
