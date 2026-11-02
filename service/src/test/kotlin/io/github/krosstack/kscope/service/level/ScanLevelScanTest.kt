package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.settings.SettingsWorkspace
import io.github.krosstack.kscope.service.settings.canonicalDump
import io.github.krosstack.kscope.service.trust.TrustStore
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanLevelScanTest {
    private val home: Path = Files.createTempDirectory("kscope-level-home")
    private val trust = TrustStore(home.resolve(TrustStore.FILE_NAME))

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(home)
    }

    private fun <T> SettingsWorkspace.session(options: OpenOptions = options(), block: (KrosScope) -> T): T =
        KrosScope.open(root, options, { null }, trust).use(block)

    private fun SettingsWorkspace.markIndexExact() {
        DriverManager.getConnection("jdbc:sqlite:$indexFile").use { connection ->
            connection.createStatement().use {
                assertEquals(
                    1,
                    it.executeUpdate(
                        "UPDATE producer SET name = 'kscope.build.gradle.tooling' " +
                            "WHERE name = 'kscope.build.gradle.static'",
                    ),
                )
            }
        }
    }

    @Test
    fun `a first scan runs at resolved and says so`() {
        SettingsWorkspace().use { workspace ->
            workspace.session {
                assertNull(it.indexLevel())
                assertNull(it.info().indexLevel)
                assertEquals("resolved", it.scan().indexLevel)
                assertEquals(ScanLevel.RESOLVED, it.indexLevel())
                assertEquals("resolved", it.info().indexLevel)
            }
        }
    }

    @Test
    fun `the resolved index is the same with and without the level being asked for`() {
        SettingsWorkspace().use { plain ->
            SettingsWorkspace().use { asked ->
                val expected = plain.session {
                    it.scan()
                    it.canonicalDump()
                }
                val actual = asked.session(OpenOptions(indexDirectory = asked.index, level = ScanLevel.RESOLVED)) {
                    it.scan()
                    it.canonicalDump()
                }
                assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun `a settings file that asks for exact runs at resolved with a notice until the workspace is trusted`() {
        SettingsWorkspace().use { workspace ->
            workspace.write("kscope.toml", "level = \"exact\"\n")

            val result = workspace.session { it.scan() }

            assertEquals("resolved", result.indexLevel)
            val notice = result.diagnostics.entries.single { it.code == "level.exact-needs-trust" }
            assertEquals(LevelPolicy.EXACT_NEEDS_TRUST, notice.message)
            assertEquals(1, result.diagnostics.warnings)
            assertEquals(0, result.diagnostics.errors)
        }
    }

    @Test
    fun `a plain scan of an exact index is refused where the build is not trusted and changes nothing`() {
        SettingsWorkspace().use { workspace ->
            workspace.session { it.scan() }
            workspace.markIndexExact()
            val before = workspace.session { it.canonicalDump() }

            val failure = assertFailsWith<LevelRefusedException> { workspace.session { it.scan() } }
            val rebuilt = assertFailsWith<LevelRefusedException> { workspace.session { it.scan(rebuild = true) } }

            assertTrue(failure.message.orEmpty().contains("kscope trust"))
            assertEquals(failure.message, rebuilt.message)
            workspace.session {
                assertEquals(ScanLevel.EXACT, it.indexLevel())
                assertEquals(before, it.canonicalDump())
            }
        }
    }

    @Test
    fun `an explicit shallower level needs a rebuild and then replaces the exact index`() {
        SettingsWorkspace().use { workspace ->
            workspace.session { it.scan() }
            workspace.markIndexExact()
            val shallower = OpenOptions(indexDirectory = workspace.index, level = ScanLevel.RESOLVED)
            val before = workspace.session { it.canonicalDump() }

            val failure = assertFailsWith<LevelRefusedException> { workspace.session(shallower) { it.scan() } }
            workspace.session { assertEquals(before, it.canonicalDump()) }
            val result = workspace.session(shallower) { it.scan(rebuild = true) }

            assertTrue(failure.message.orEmpty().contains("--rebuild"))
            assertEquals("resolved", result.indexLevel)
            workspace.session { assertEquals(ScanLevel.RESOLVED, it.indexLevel()) }
        }
    }

    @Test
    fun `trust is revocable and the settings report shows it`() {
        SettingsWorkspace().use { workspace ->
            assertFalse(workspace.session { it.info() }.settings.level.trusted)

            trust.grant(workspace.root)
            assertTrue(workspace.session { it.info() }.settings.level.trusted)

            trust.revoke(workspace.root)
            assertFalse(workspace.session { it.info() }.settings.level.trusted)
        }
    }

    @Test
    fun `info shows the level that the settings ask for and where it comes from`() {
        SettingsWorkspace().use { workspace ->
            val defaulted = workspace.session { it.info() }.settings.level
            workspace.write("kscope.toml", "level = \"exact\"\n")
            val asked = workspace.session { it.info() }.settings.level

            assertEquals("resolved", defaulted.value)
            assertEquals("default", defaulted.origin)
            assertEquals("exact", asked.value)
            assertEquals("kscope.toml", asked.origin)
        }
    }

    @Test
    fun `a trusted workspace is reported as trusted even when the file asks for resolved`() {
        SettingsWorkspace().use { workspace ->
            trust.grant(workspace.root)
            workspace.write("kscope.toml", "level = \"resolved\"\n")

            val result = workspace.session { it.scan() }

            assertEquals("resolved", result.indexLevel)
            assertTrue(result.diagnostics.entries.none { it.code == "level.exact-needs-trust" })
        }
    }
}
