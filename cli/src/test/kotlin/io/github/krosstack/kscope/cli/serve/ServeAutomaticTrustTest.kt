package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.level.automaticScanLevel
import io.github.krosstack.kscope.service.trust.TrustStore
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServeAutomaticTrustTest {
    private val buildRan =
        ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), "", "exact")

    private fun <T> inTrustedWorkspace(exactIndex: Boolean, block: (Path, TrustStore) -> T): T = inWorkspace { root ->
        writeFile(root, "kscope.toml", "level = \"exact\"\n")
        check(runKscope("scan", "--workspace", root.toString()).statusCode == 0) { "the workspace did not scan" }
        if (exactIndex) markIndexExact(root)
        val trust = TrustStore.ofThisMachine()
        trust.grant(root)
        try {
            assertEquals(ScanLevel.EXACT, KrosScope.open(root).use { it.automaticScanLevel() })
            block(root.toRealPath(), trust)
        } finally {
            trust.revoke(root)
        }
    }

    private fun markIndexExact(root: Path) {
        DriverManager.getConnection("jdbc:sqlite:${root.resolve(".kscope/index.db")}").use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    "UPDATE producer SET name = 'kscope.build.gradle.tooling' " +
                        "WHERE name = 'kscope.build.gradle.static'",
                )
            }
        }
    }

    private fun updateWith(root: Path, runner: ScanRunner): Int =
        ServeRequests(root, {}, runner, {}).use { it.update() }

    @Test
    fun `an automatic scan that goes to exact carries no authorization of its own`() =
        inTrustedWorkspace(exactIndex = false) { root, _ ->
            val seen = AtomicReference<OpenOptions>()

            val code = updateWith(root) { _, options, _, _ ->
                seen.set(options)
                buildRan
            }

            assertEquals(0, code)
            assertFalse(seen.get().trustBuild)
            assertNull(seen.get().level)
        }

    @Test
    fun `a trust revoked while the automatic scan waits turns it into a scan at resolved`() =
        inTrustedWorkspace(exactIndex = false) { root, trust ->
            val outcome = AtomicReference<ScanResult>()

            val code = updateWith(root) { workspace, options, rebuild, verify ->
                trust.revoke(workspace)
                val result = if (options.trustBuild) {
                    buildRan
                } else {
                    KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }
                }
                result.also(outcome::set)
            }

            assertEquals(0, code)
            assertEquals("resolved", outcome.get().indexLevel)
            assertTrue(outcome.get().diagnostics.entries.any { it.code == "level.exact-needs-trust" })
        }

    @Test
    fun `a trust revoked while the automatic scan waits leaves an exact index as it is`() =
        inTrustedWorkspace(exactIndex = true) { root, trust ->
            val buildStarted = AtomicReference(false)

            val code = updateWith(root) { workspace, options, rebuild, verify ->
                trust.revoke(workspace)
                if (options.trustBuild) {
                    buildStarted.set(true)
                    buildRan
                } else {
                    KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }
                }
            }

            assertEquals(1, code)
            assertFalse(buildStarted.get())
            assertEquals(ScanLevel.EXACT, KrosScope.open(root).use { it.indexLevel() })
        }
}
