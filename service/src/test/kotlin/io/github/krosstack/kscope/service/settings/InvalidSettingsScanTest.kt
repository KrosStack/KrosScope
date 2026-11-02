package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.ServiceJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A settings file that is not valid stops a scan before it changes anything, and only a scan. */
class InvalidSettingsScanTest {
    private val settingsFile = "kscope.toml"
    private val invalid = "format = 1\n\n[resources]\nworkers = 99\n"

    private companion object {
        const val LIB = "lib/src/main/kotlin/Lib.kt"
    }

    private fun snapshotOf(directory: Path): Map<String, List<Byte>> = Files.walk(directory).use { paths ->
        paths.filter(Files::isRegularFile).toList()
            .associate { directory.relativize(it).toString() to Files.readAllBytes(it).toList() }
    }

    private fun scannedWorkspace(): SettingsWorkspace = SettingsWorkspace().also { workspace ->
        workspace.open { it.scan() }
    }

    @Test
    fun `every kind of scan is refused and the index is byte for byte what it was`() {
        scannedWorkspace().use { workspace ->
            val before = snapshotOf(workspace.index)
            workspace.write(settingsFile, invalid)

            workspace.open { scope ->
                assertFailsWith<InvalidSettingsException> { scope.scan() }
                assertFailsWith<InvalidSettingsException> { scope.scan(verify = true) }
                assertFailsWith<InvalidSettingsException> { scope.scan(rebuild = true) }
                assertFailsWith<InvalidSettingsException> { scope.scan(rebuild = true, verify = true) }
            }

            assertEquals(before, snapshotOf(workspace.index))
        }
    }

    @Test
    fun `the refusal names the file the line and the key`() {
        scannedWorkspace().use { workspace ->
            workspace.write(settingsFile, invalid)

            val failure = workspace.open { scope -> assertFailsWith<InvalidSettingsException> { scope.scan() } }

            assertEquals(listOf(SettingsIssue(4, "resources.workers", "must be between 1 and 8")), failure.issues)
            assertTrue("kscope.toml:4: resources.workers: must be between 1 and 8" in failure.message.orEmpty())
        }
    }

    @Test
    fun `a first scan that is refused creates no index`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, invalid)

            workspace.open { scope -> assertFailsWith<InvalidSettingsException> { scope.scan() } }

            assertFalse(Files.exists(workspace.indexFile))
            assertFalse(Files.exists(workspace.root.resolve(".kscope")))
        }
    }

    private fun answers(workspace: SettingsWorkspace): List<String> = workspace.open { scope ->
        listOf(
            ServiceJson.encode(scope.analyze("lib")),
            ServiceJson.encode(scope.symbol("Lib")),
            ServiceJson.encode(scope.impact(LIB)),
            ServiceJson.encode(scope.related(LIB)),
            ServiceJson.encode(scope.graphView()),
        )
    }

    @Test
    fun `queries do not read the file and answer as before`() {
        scannedWorkspace().use { workspace ->
            val expected = answers(workspace)
            workspace.write(settingsFile, invalid)

            assertEquals(expected, answers(workspace))
        }
    }

    @Test
    fun `info reports the errors instead of failing`() {
        scannedWorkspace().use { workspace ->
            workspace.write(settingsFile, invalid)

            val info = workspace.open { it.info() }

            assertTrue(info.indexed)
            assertEquals("kscope.toml", info.settings.file)
            assertFalse(info.settings.valid)
            assertEquals(listOf(SettingsIssue(4, "resources.workers", "must be between 1 and 8")), info.settings.errors)
        }
    }

    @Test
    fun `once the file is fixed the next scan runs and changes nothing that was indexed`() {
        scannedWorkspace().use { workspace ->
            val dumpBefore = workspace.open { it.canonicalDump() }
            workspace.write(settingsFile, invalid)
            workspace.open { scope -> assertFailsWith<InvalidSettingsException> { scope.scan() } }
            workspace.write(settingsFile, "[resources]\nworkers = 2\n")

            val result = workspace.open { scope -> scope.scan() to scope.canonicalDump() }

            assertEquals(0, result.first.unitsProduced)
            assertEquals(dumpBefore, result.second)
        }
    }

    @Test
    fun `a directory instead of the file refuses a scan in the same way`() {
        scannedWorkspace().use { workspace ->
            val before = snapshotOf(workspace.index)
            Files.createDirectory(workspace.root.resolve(settingsFile))

            val failure = workspace.open { scope -> assertFailsWith<InvalidSettingsException> { scope.scan() } }

            assertEquals(listOf(SettingsIssue(null, null, "must be a regular file")), failure.issues)
            assertEquals(before, snapshotOf(workspace.index))
        }
    }
}
