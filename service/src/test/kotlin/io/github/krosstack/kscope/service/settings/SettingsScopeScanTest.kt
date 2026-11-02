package io.github.krosstack.kscope.service.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The scan settings narrow what a scan reads, and the index of a workspace reaches the same state either way. */
class SettingsScopeScanTest {
    private val settingsFile = "kscope.toml"
    private val libOnly = "[scan]\ninclude = [\"lib/**\"]\n"
    private val libTest = "lib/src/test/kotlin/LibTest.kt"

    private fun SettingsWorkspace.scan() = open { it.scan() }

    private fun SettingsWorkspace.dump() = open { it.canonicalDump() }

    private fun SettingsWorkspace.paths() = open { it.artifactPaths() }

    private fun SettingsWorkspace.withTests() = apply { write(libTest, "class LibTest\n") }

    @Test
    fun `a full scan then a narrow scan then a full scan again ends in the first index`() {
        SettingsWorkspace().withTests().use { workspace ->
            workspace.scan()
            val first = workspace.dump()
            val all = workspace.paths()

            workspace.write(settingsFile, libOnly)
            workspace.scan()
            val narrow = workspace.paths()

            workspace.delete(settingsFile)
            workspace.scan()

            assertTrue("README.md" in all && "README.md" !in narrow)
            assertTrue("settings.gradle.kts" in narrow, "build files stay readable: $narrow")
            assertEquals(first, workspace.dump())
        }
    }

    @Test
    fun `a narrow incremental scan equals a clean scan of the same settings`() {
        SettingsWorkspace().withTests().use { clean ->
            clean.write(settingsFile, libOnly)
            clean.scan()
            SettingsWorkspace().withTests().use { incremental ->
                incremental.scan()
                incremental.write(settingsFile, libOnly)
                val verification = incremental.open { it.scan(verify = true) }.verification

                assertEquals(true, verification?.matches)
                assertEquals(clean.dump(), incremental.dump())
            }
        }
    }

    @Test
    fun `editing the scan settings redoes the difference and an edit of resources redoes nothing`() {
        SettingsWorkspace().withTests().use { workspace ->
            workspace.scan()
            workspace.write(settingsFile, "[scan]\ntests = false\n")
            val narrowed = workspace.scan()
            workspace.write(settingsFile, "[scan]\ntests = false\n[resources]\nworkers = 2\n")
            val resourcesOnly = workspace.scan()

            assertTrue(narrowed.unitsProduced > 0)
            assertEquals(0, resourcesOnly.unitsProduced)
            assertEquals(0, resourcesOnly.unitsRemoved)
            assertTrue(libTest !in workspace.paths())
        }
    }

    @Test
    fun `a pattern that matches nothing is a scan note`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "[scan]\nexclude = [\"nowhere\"]\n")

            val messages = workspace.scan().diagnostics.entries.map { it.message }

            assertTrue(messages.any { "scan.exclude" in it && "'nowhere'" in it }, "$messages")
        }
    }

    @Test
    fun `info says whether the index was built with the settings of the file`() {
        SettingsWorkspace().use { workspace ->
            assertEquals("not-indexed", workspace.open { it.info() }.settings.scan.indexState)
            workspace.scan()
            assertEquals("current", workspace.open { it.info() }.settings.scan.indexState)

            workspace.write(settingsFile, libOnly)
            val outdated = workspace.open { it.info() }.settings.scan
            assertEquals("outdated", outdated.indexState)
            assertEquals(listOf("lib/**"), outdated.include)

            workspace.scan()
            assertEquals("current", workspace.open { it.info() }.settings.scan.indexState)

            workspace.write(settingsFile, "[scan]\ninclude = [\"/lib\"]\n")
            assertEquals("unknown", workspace.open { it.info() }.settings.scan.indexState)
        }
    }

    @Test
    fun `the fixed defaults of a benchmark ignore the scan settings of the file`() {
        SettingsWorkspace().use { workspace ->
            val fixed = workspace.options().copy(settings = WorkspaceSettings.DEFAULTS)
            val without = workspace.open(fixed) {
                it.scan()
                it.canonicalDump()
            }
            workspace.write(settingsFile, libOnly)
            val with = workspace.open(fixed) {
                it.scan(rebuild = true)
                it.canonicalDump()
            }

            assertEquals(without, with)
            assertNotEquals(0, with.length)
        }
    }

    @Test
    fun `before the first scan the watcher looks at the files the file selects`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, libOnly)
            val source = SettingsSource(workspace.root, null, { null })

            assertEquals(listOf("lib/**"), source.scope().include)
            workspace.write(settingsFile, "[scan]\ninclude = [\"/lib\"]\n")
            assertEquals(emptyList(), source.scope().include)
        }
    }
}
