package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.KrosScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsReportWithoutOpeningTest {
    @Test
    fun `the report of a workspace without a file names none and creates nothing`() {
        SettingsWorkspace().use { workspace ->
            val report = KrosScope.settingsReport(workspace.root)

            assertNull(report.file)
            assertTrue(report.valid)
            assertFalse(workspace.root.resolve(".kscope").toFile().exists())
        }
    }

    @Test
    fun `the report reads the file as info does`() {
        SettingsWorkspace().use { workspace ->
            workspace.write("kscope.toml", "[resources]\nworkers = 3\n")

            val report = KrosScope.settingsReport(workspace.root)

            assertEquals("kscope.toml", report.file)
            assertEquals(3, report.resources.workers.value)
        }
    }

    @Test
    fun `the report lists the problems of an invalid file`() {
        SettingsWorkspace().use { workspace ->
            workspace.write("kscope.toml", "[resources]\nworkers = \"many\"\n")

            val report = KrosScope.settingsReport(workspace.root)

            assertFalse(report.valid)
            assertTrue(report.errors.isNotEmpty())
        }
    }
}
