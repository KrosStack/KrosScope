package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.settings.SettingsWorkspace
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Only a scan replaces an index of another schema version; reading the index leaves it as it is. */
class IncompatibleIndexTest {
    private fun SettingsWorkspace.scanAndPlantOtherSchemaVersion() {
        open { it.scan() }
        connection().use { it.createStatement().use { statement -> statement.execute("PRAGMA user_version = 32767") } }
    }

    private fun SettingsWorkspace.connection(): Connection = DriverManager.getConnection("jdbc:sqlite:$indexFile")

    private fun SettingsWorkspace.number(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                assertTrue(rows.next())
                rows.getInt(1)
            }
        }
    }

    private fun SettingsWorkspace.assertKept() {
        assertEquals(32767, number("PRAGMA user_version"))
        assertTrue(number("SELECT COUNT(*) FROM artifact") > 0)
    }

    @Test
    fun `info reports no index and keeps an index of another schema version`() {
        SettingsWorkspace().use { workspace ->
            workspace.scanAndPlantOtherSchemaVersion()

            workspace.open {
                assertFalse(it.info().indexed)
                assertFalse(it.isIndexed())
            }

            workspace.assertKept()
        }
    }

    @Test
    fun `the graph and the search index of an index of another schema version are not indexed and kept`() {
        SettingsWorkspace().use { workspace ->
            workspace.scanAndPlantOtherSchemaVersion()

            workspace.open {
                assertFailsWith<NotIndexedException> { it.graph() }
                assertFailsWith<NotIndexedException> { it.searchIndex() }
            }

            workspace.assertKept()
        }
    }

    @Test
    fun `a scan replaces an index of another schema version and says so`() {
        SettingsWorkspace().use { workspace ->
            workspace.scanAndPlantOtherSchemaVersion()

            workspace.open {
                val result = it.scan()

                assertTrue(result.diagnostics.entries.any { entry -> entry.code == "index.rebuilt" })
                assertTrue(it.info().indexed)
            }
        }
    }
}
