package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.Severity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scans a small Kotlin Multiplatform and JVM workspace with the real analyzer worker. The static reading of its Gradle
 * scripts says that `:app` depends on `:core`, so only the source sets that depend on a change are analysed again.
 */
class KotlinAnalysisIntegrationTest {
    private val sample = SampleWorkspace("mixed")
    private val greeter = "core/src/commonMain/kotlin/mixed/core/Greeter.kt"
    private val mainSource = "app/src/main/kotlin/mixed/app/Main.kt"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `a full scan produces symbols and relationships of kotlin and java sources`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertEquals(0, scan.diagnostics.warnings, "${scan.diagnostics.entries}")
            assertEquals(
                listOf("history.not-available"),
                scan.diagnostics.entries.filter { it.severity == Severity.INFO }.map { it.code },
                "Dependencies are known, not assumed; the sample is no Git work tree",
            )
            val info = service.info()
            assertEquals(3L, info.symbolsByKind["kotlin.class"])
            assertEquals(1L, info.symbolsByKind["kotlin.interface"])
            assertEquals(2L, info.symbolsByKind["java.class"])
            assertEquals(2L, info.symbolsByKind["java.method"])
            val resolved = info.coverage.filter { it.state == "exact" && it.evidence == "compiler" }
            assertTrue(resolved.any { it.relation == "calls" }, "${info.coverage}")
            assertTrue(resolved.any { it.relation == "implements" }, "${info.coverage}")
        }
    }

    @Test
    fun `a second scan skips every unit and a change redoes the source sets that depend on it`() {
        KrosScope.open(sample.root).use { service ->
            val first = service.scan()
            assertEquals(
                18 + HISTORY_UNIT + linkerCount,
                first.unitsProduced,
                "Discovery, the build model, four source sets, the build config unit of each of two build scripts, " +
                    "the load calls and the Ktor routes of each of the four source sets, the generator inputs and " +
                    "the copy sources of the workspace, the history and the linkers",
            )

            val second = service.scan()
            assertEquals(0, second.unitsProduced)
            assertEquals(18 + HISTORY_UNIT + linkerCount, second.unitsSkipped)

            sample.write("README.md", "# Mixed sample, edited\n")
            val documentation = service.scan()
            assertEquals(1 + linkerCount, documentation.unitsProduced, "Discovery sees it, the linkers follow")

            sample.write(mainSource, "package mixed.app\n\nfun main() = Unit\n")
            val app = service.scan()
            assertEquals(
                7 + linkerCount,
                app.unitsProduced,
                "Discovery, the two source sets of the app, the load calls and the Ktor routes of each of the two, " +
                    "the linkers",
            )
            assertEquals(11 + HISTORY_UNIT, app.unitsSkipped)

            sample.write(greeter, "package mixed.core\n\ninterface Greeter\n")
            val core = service.scan()
            assertEquals(
                13 + linkerCount,
                core.unitsProduced,
                "Discovery, the source sets of the core and of the app, the load calls and the Ktor routes of each " +
                    "of the four, the linkers",
            )
        }
    }

    @Test
    fun `verification compares the incremental result with a clean rebuild`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            sample.write(greeter, "package mixed.core\n\ninterface Greeter\n")
            val verification = assertNotNull(service.scan(verify = true).verification)
            assertTrue(verification.matches, "${verification.differences}")
        }
    }
}
