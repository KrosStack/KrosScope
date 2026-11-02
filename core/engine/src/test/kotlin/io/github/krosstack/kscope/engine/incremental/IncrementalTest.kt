package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.UpdateReport
import io.github.krosstack.kscope.engine.VerifyReport
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.model.OwnershipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Each test changes the tree after a first scan and requires the incremental result to equal a clean rebuild. */
class IncrementalTest {
    private val artifactProducer = RecordingProducer("by-artifact")
    private val sourceSetProducer = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"

    /** What the incremental update did, and whether its result equals a rebuild. */
    private class Outcome(
        val report: UpdateReport,
        val artifactUnits: List<String>,
        val sourceSetUnits: List<String>,
        val verification: VerifyReport,
    )

    private fun changed(change: (TestWorkspace) -> Unit): Outcome = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine(coreRegistry(artifactProducer, sourceSetProducer)).use { engine ->
            engine.update()
            artifactProducer.calls.clear()
            sourceSetProducer.calls.clear()
            change(workspace)
            val report = engine.update()
            val artifactUnits = artifactProducer.producedKeys
            val sourceSetUnits = sourceSetProducer.producedKeys
            val verification = assertNotNull(engine.update(verify = true).verify)
            assertTrue(verification.matches, "Incremental differs from rebuild: ${verification.differences}")
            Outcome(report, artifactUnits, sourceSetUnits, verification)
        }
    }

    @Test
    fun `modifying a file produces its artifact unit and invalidates every source set conservatively`() {
        val outcome = changed { it.write(greeter, "class Greeter\n") }
        assertTrue(outcome.report.unitsSkipped > 0)
        assertTrue(outcome.artifactUnits.single().endsWith(":$greeter"))
        assertEquals(SOURCE_SET_UNITS, outcome.sourceSetUnits.size)
    }

    @Test
    fun `adding a file produces its unit`() {
        val outcome = changed { it.write("core/src/commonMain/kotlin/com/example/core/Extra.kt", "class Extra\n") }
        assertTrue(outcome.report.unitsSkipped > 0)
        assertTrue(outcome.artifactUnits.single().endsWith("Extra.kt"))
    }

    @Test
    fun `deleting a file removes its unit and the source set unit that lost its last input`() {
        val outcome = changed { it.delete(greeter) }
        assertEquals(2, outcome.report.unitsRemoved)
        assertTrue(outcome.report.unitsSkipped > 0)
        assertTrue(outcome.artifactUnits.isEmpty())
    }

    @Test
    fun `renaming a file removes the old unit and produces the new one`() {
        val outcome = changed { it.move(greeter, "core/src/commonMain/kotlin/com/example/core/Welcomer.kt") }
        assertEquals(1, outcome.report.unitsRemoved)
        assertTrue(outcome.artifactUnits.single().endsWith("Welcomer.kt"))
    }

    @Test
    fun `adding a module extends the build model and produces its source set`() {
        val outcome = changed {
            it.write("settings.gradle.kts", "include(\":extra\")\n")
            it.write("extra/build.gradle.kts", "")
            it.write("extra/src/main/kotlin/Extra.kt", "class Extra\n")
        }
        assertTrue(outcome.report.unitsSkipped > 0)
        assertTrue(outcome.sourceSetUnits.any { it.endsWith(":extra@main") })
    }

    @Test
    fun `editing a build script that leaves the model unchanged produces no source unit`() {
        val outcome = changed { it.write("app/build.gradle.kts", "plugins { id(\"com.android.library\") }\n") }
        assertTrue(outcome.report.unitsSkipped > 0)
        assertTrue(outcome.artifactUnits.isEmpty() && outcome.sourceSetUnits.isEmpty())
    }

    @Test
    fun `removing a module removes the units of its source sets`() {
        val outcome = changed { it.delete("core/build.gradle.kts") }
        assertEquals(CORE_SOURCE_SETS, outcome.report.unitsRemoved)
    }

    @Test
    fun `changing nothing produces nothing`() {
        val outcome = changed { }
        assertEquals(0, outcome.report.unitsProduced)
        assertEquals(0, outcome.report.unitsRemoved)
    }

    private companion object {
        const val SOURCE_SET_UNITS = 6
        const val CORE_SOURCE_SETS = 3
    }
}
