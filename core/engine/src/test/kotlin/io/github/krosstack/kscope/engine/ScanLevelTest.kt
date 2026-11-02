package io.github.krosstack.kscope.engine

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanLevelTest {
    @Test
    fun `a scan at level exact runs trusted`() {
        val root = Path.of("workspace")

        assertFalse(WorkspaceConfig(root).withSettingsFixed().trusted)
        assertTrue(WorkspaceConfig(root, trusted = true).withSettingsFixed().trusted)
        assertEquals(ScanLevel.EXACT, WorkspaceConfig(root, trusted = true).level())
        assertTrue(WorkspaceConfig(root, level = { ScanLevel.EXACT }).withSettingsFixed().trusted)
        assertFalse(WorkspaceConfig(root, trusted = true, level = { ScanLevel.RESOLVED }).withSettingsFixed().trusted)
    }

    @Test
    fun `a scan asks for the level once and keeps it`() {
        var asked = 0
        var current = ScanLevel.RESOLVED
        val config = WorkspaceConfig(
            Path.of("workspace"),
            level = {
                asked++
                current
            },
        )

        val fixed = config.withSettingsFixed()
        current = ScanLevel.EXACT

        assertEquals(1, asked)
        assertEquals(ScanLevel.RESOLVED, fixed.level())
        assertEquals(ScanLevel.RESOLVED, fixed.level())
        assertEquals(1, asked)
    }

    @Test
    fun `a workspace that was never scanned has no level`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { assertNull(it.indexLevel()) }
        }
    }

    @Test
    fun `the level is read from the persisted build model provider`() {
        val registry = coreRegistry(providers = CoreCapabilities.buildModelProviders() + FakeTrustedProvider())
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use {
                it.update()
                assertEquals(ScanLevel.RESOLVED, it.indexLevel())
            }
            workspace.engine(registry, trusted = true).use {
                it.update()
                assertEquals(ScanLevel.EXACT, it.indexLevel())
            }
            workspace.engine(registry).use {
                assertEquals(ScanLevel.EXACT, it.indexLevel())
                it.update()
                assertEquals(ScanLevel.RESOLVED, it.indexLevel())
            }
        }
    }

    @Test
    fun `a level provider decides the build model of each scan`() {
        val registry = coreRegistry(providers = CoreCapabilities.buildModelProviders() + FakeTrustedProvider())
        var level = ScanLevel.EXACT
        TestWorkspace("gradle-multi").use { workspace ->
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, level = { level })
            ScanEngine(registry, config).use {
                it.update()
                assertEquals(ScanLevel.EXACT, it.indexLevel())
                level = ScanLevel.RESOLVED
                it.update()
                assertEquals(ScanLevel.RESOLVED, it.indexLevel())
            }
        }
    }
}
