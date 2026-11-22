package io.github.krosstack.kscope.engine.inspection

import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.engine.FakeTrustedProvider
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.ScanLevel
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.indexLevel
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IndexInspectionTest {
    private val trustedRegistry =
        coreRegistry(providers = CoreCapabilities.buildModelProviders() + FakeTrustedProvider())

    private fun levelOf(inspection: IndexInspection): ScanLevel = assertIs<IndexInspection.Readable>(inspection).level

    @Test
    fun `a workspace that was never scanned has no index and nothing is created`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use {
                assertEquals(IndexInspection.NoIndex, it.inspectIndex())
                assertFalse(it.hasAnyIndex())
            }
            assertTrue(Files.notExists(workspace.indexDirectory))
        }
    }

    @Test
    fun `the level is the one that the index reports, on a syntax, a resolved and an exact index`() {
        TestWorkspace("gradle-multi").use { workspace ->
            val syntax = WorkspaceConfig(workspace.root, workspace.indexDirectory, level = { ScanLevel.SYNTAX })
            ScanEngine(trustedRegistry, syntax).use { engine ->
                engine.update()
                assertEquals(ScanLevel.SYNTAX, levelOf(engine.inspectIndex()))
                assertEquals(engine.indexLevel(), levelOf(engine.inspectIndex()))
            }
            workspace.engine(trustedRegistry).use { engine ->
                engine.rebuild()
                assertEquals(ScanLevel.RESOLVED, levelOf(engine.inspectIndex()))
                assertEquals(engine.indexLevel(), levelOf(engine.inspectIndex()))
            }
            workspace.engine(trustedRegistry, trusted = true).use { engine ->
                engine.rebuild()
                assertEquals(ScanLevel.EXACT, levelOf(engine.inspectIndex()))
                assertEquals(engine.indexLevel(), levelOf(engine.inspectIndex()))
            }
        }
    }

    @Test
    fun `the repositories are those of the structure of the index`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { engine ->
                engine.update()

                val inspected = assertIs<IndexInspection.Readable>(engine.inspectIndex())

                assertEquals(engine.graph().structure().repositories, inspected.repositories)
            }
        }
    }

    @Test
    fun `an index of another version is reported as such, counts as an index file and stays as it is`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { it.update() }
            val file = workspace.indexDirectory.resolve("index.db")
            DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA user_version = 1") }
            }
            val before = Files.readAllBytes(file).toList()

            workspace.engine().use { engine ->
                val inspection = assertIs<IndexInspection.OtherFormat>(engine.inspectIndex())
                assertEquals(1, inspection.found)
                assertTrue(engine.hasAnyIndex())
                assertFalse(engine.hasIndexedFiles())
            }

            assertEquals(before, Files.readAllBytes(file).toList())
        }
    }

    @Test
    fun `a file that is not a database is damaged`() {
        TestWorkspace("gradle-multi").use { workspace ->
            Files.createDirectories(workspace.indexDirectory)
            Files.writeString(workspace.indexDirectory.resolve("index.db"), "not a database")

            workspace.engine().use { assertIs<IndexInspection.Damaged>(it.inspectIndex()) }
        }
    }
}
