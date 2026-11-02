package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.settings.SettingsWorkspace
import io.github.krosstack.kscope.service.trust.TrustStore
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DamagedIndexRebuildTest {
    private val home: Path = Files.createTempDirectory("kscope-damaged-home")
    private val trust = TrustStore(home.resolve(TrustStore.FILE_NAME))

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(home)
    }

    private fun <T> SettingsWorkspace.session(options: OpenOptions = options(), block: (KrosScope) -> T): T =
        KrosScope.open(root, options, { null }, trust).use(block)

    private fun SettingsWorkspace.scanThenDamage(damage: String) {
        session { it.scan() }
        DriverManager.getConnection("jdbc:sqlite:$indexFile").use { connection ->
            connection.createStatement().use { it.executeUpdate(damage) }
        }
    }

    @Test
    fun `an explicit rebuild replaces an index whose ownership records cannot be read`() {
        for (damage in OWNERSHIP_DAMAGE) {
            SettingsWorkspace().use { workspace ->
                workspace.scanThenDamage(damage)
                val explicit = OpenOptions(indexDirectory = workspace.index, level = ScanLevel.RESOLVED)

                val result = workspace.session(explicit) { it.scan(rebuild = true) }

                assertEquals("resolved", result.indexLevel, damage)
                workspace.session { assertEquals(ScanLevel.RESOLVED, it.indexLevel(), damage) }
            }
        }
    }

    @Test
    fun `a plain rebuild replaces an index whose ownership records cannot be read`() {
        for (damage in OWNERSHIP_DAMAGE) {
            SettingsWorkspace().use { workspace ->
                workspace.scanThenDamage(damage)

                val result = workspace.session { it.scan(rebuild = true) }

                assertEquals("resolved", result.indexLevel, damage)
                workspace.session { assertEquals(ScanLevel.RESOLVED, it.indexLevel(), damage) }
            }
        }
    }

    @Test
    fun `a scan that is not a rebuild still fails on an index whose ownership records cannot be read`() {
        for (damage in OWNERSHIP_DAMAGE) {
            SettingsWorkspace().use { workspace ->
                workspace.scanThenDamage(damage)

                assertFails(damage) { workspace.session { it.scan() } }
            }
        }
    }

    private companion object {
        val OWNERSHIP_DAMAGE = listOf("DROP TABLE owner_unit", "UPDATE owner_unit SET kind = 'unknown-kind'")
    }
}
