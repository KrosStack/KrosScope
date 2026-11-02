package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.settings.SettingsWorkspace
import io.github.krosstack.kscope.service.trust.TrustStore
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticLevelTrustTest {
    private val home: Path = Files.createTempDirectory("kscope-automatic-home")
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
                it.executeUpdate(
                    "UPDATE producer SET name = 'kscope.build.gradle.tooling' " +
                        "WHERE name = 'kscope.build.gradle.static'",
                )
            }
        }
    }

    private fun SettingsWorkspace.firstScanAtSyntax() {
        session(OpenOptions(indexDirectory = index, level = ScanLevel.SYNTAX)) { it.scan() }
    }

    @Test
    fun `stored trust takes a syntax index that asks for exact to exact, and none stops at resolved`() {
        SettingsWorkspace().use { workspace ->
            workspace.write("kscope.toml", "level = \"exact\"\n")
            workspace.session { assertEquals(ScanLevel.SYNTAX, it.automaticScanLevel()) }
            workspace.firstScanAtSyntax()

            workspace.session {
                assertEquals(ScanLevel.RESOLVED, it.automaticScanLevel())
                assertTrue(it.indexIsShallow())
            }
            trust.grant(workspace.root)
            workspace.session {
                assertEquals(ScanLevel.EXACT, it.automaticScanLevel())
                assertTrue(it.indexIsShallow())
            }
        }
    }

    @Test
    fun `revoked trust stops the automatic update of an exact index and never asks for the build`() {
        SettingsWorkspace().use { workspace ->
            workspace.write("kscope.toml", "level = \"exact\"\n")
            workspace.session { it.scan() }
            workspace.markIndexExact()
            trust.grant(workspace.root)
            workspace.session {
                assertEquals(ScanLevel.EXACT, it.automaticScanLevel())
                assertFalse(it.automaticUpdatesBlocked())
            }

            trust.revoke(workspace.root)

            workspace.session {
                assertEquals(ScanLevel.RESOLVED, it.automaticScanLevel())
                assertTrue(it.automaticUpdatesBlocked())
                assertFalse(it.indexIsShallow())
            }
        }
    }

    @Test
    fun `a workspace whose file does not ask for exact stays at resolved even when trusted`() {
        SettingsWorkspace().use { workspace ->
            trust.grant(workspace.root)
            workspace.firstScanAtSyntax()

            workspace.session { assertEquals(ScanLevel.RESOLVED, it.automaticScanLevel()) }
        }
    }
}
