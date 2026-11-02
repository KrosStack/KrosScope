package io.github.krosstack.kscope.service.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Editing the settings file only changes how the next scan runs: it never makes a scan redo work. */
class SettingsNoReworkTest {
    private val settingsFile = "kscope.toml"

    private fun SettingsWorkspace.scan() = open { it.scan() }

    @Test
    fun `a scan after editing only the settings file produces no unit`() {
        SettingsWorkspace().use { workspace ->
            assertTrue(workspace.scan().unitsProduced > 0)
            assertEquals(0, workspace.scan().unitsProduced)

            workspace.write(settingsFile, "format = 1\n\n[resources]\nworkers = 2\nworker-heap-mib = 512\n")
            val added = workspace.scan()
            workspace.write(settingsFile, "[resources]\nworkers = 6\nworker-heap-mib = 4096\n")
            val changed = workspace.scan()
            workspace.delete(settingsFile)
            val removed = workspace.scan()

            for (result in listOf(added, changed, removed)) {
                assertEquals(0, result.unitsProduced)
                assertEquals(0, result.unitsRemoved)
                assertTrue(result.unitsSkipped > 0)
                assertEquals(4, result.artifacts)
            }
        }
    }

    @Test
    fun `verification of an incremental scan still matches a clean rebuild with the file present`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "[resources]\nworkers = 2\n")
            workspace.scan()
            workspace.write(settingsFile, "[resources]\nworkers = 3\n")

            val verification = workspace.open { it.scan(verify = true) }.verification

            assertEquals(true, verification?.matches)
        }
    }
}
