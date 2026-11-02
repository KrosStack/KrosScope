package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.settings.SettingsWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An instance that only read the level or the summary of an index still notices that another process changed it. */
class MetadataFreshnessTest {
    private fun SettingsWorkspace.scanAt(level: ScanLevel) =
        open(OpenOptions(indexDirectory = index, level = level)) { it.scan() }

    @Test
    fun `a refresh shows the level that another process deepened the index to after the level was read`() {
        SettingsWorkspace().use { workspace ->
            workspace.scanAt(ScanLevel.SYNTAX)
            workspace.open { reader ->
                assertEquals(ScanLevel.SYNTAX, reader.indexLevel())

                workspace.scanAt(ScanLevel.RESOLVED)
                reader.refreshIndex()

                assertEquals(ScanLevel.RESOLVED, reader.indexLevel())
            }
        }
    }

    @Test
    fun `a refresh shows the level that another process deepened the index to after info was read`() {
        SettingsWorkspace().use { workspace ->
            workspace.scanAt(ScanLevel.SYNTAX)
            workspace.open { reader ->
                assertEquals("syntax", reader.info().indexLevel)

                workspace.scanAt(ScanLevel.RESOLVED)
                reader.refreshIndex()

                assertEquals("resolved", reader.info().indexLevel)
                assertEquals(ScanLevel.RESOLVED, reader.indexLevel())
            }
        }
    }

    @Test
    fun `a refresh finds the index that another process built after the level was read as absent`() {
        SettingsWorkspace().use { workspace ->
            workspace.open { reader ->
                assertNull(reader.indexLevel())

                workspace.scanAt(ScanLevel.SYNTAX)
                reader.refreshIndex()

                assertEquals(ScanLevel.SYNTAX, reader.indexLevel())
            }
        }
    }
}
