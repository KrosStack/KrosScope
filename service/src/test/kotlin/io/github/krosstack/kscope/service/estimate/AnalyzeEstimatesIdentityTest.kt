package io.github.krosstack.kscope.service.estimate

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** `analyze` prints the same estimates whether they come from block measures or from compiling a package per level. */
class AnalyzeEstimatesIdentityTest {
    @Test
    fun `every sample workspace gives the same output in every state of the cache`() {
        val names = sampleNames()
        assertTrue("tests" in names, "the samples were not found: $names")

        val differences = names.flatMap { name ->
            SampleWorkspace(name).use { sample ->
                scan(sample.root)
                EstimateIdentity(sample.root).differences(TASKS).map { "$name: $it" }
            }
        }

        assertEquals(emptyList(), differences)
    }

    @Test
    fun `the reference workspace gives the same output in every state of the cache`() {
        val fixture = referenceWorkspace()
        assumeTrue(fixture != null, "the reference workspace is not available")

        ReferenceWorkspaceCopy(checkNotNull(fixture)).use { copy ->
            scan(copy.root)
            assertEquals(emptyList(), EstimateIdentity(copy.root).differences(REFERENCE_TASKS))
        }
    }

    @Test
    fun `a file that changed and was scanned again is estimated from its new content`() {
        SampleWorkspace("tests").use { sample ->
            scan(sample.root)
            val identity = EstimateIdentity(sample.root)
            val before = identity.compiledEstimates(TASKS.first())
            identity.analyzedJson(CacheState.WARM, TASKS.first())

            sample.write(GREETER, Files.readString(sample.root.resolve(GREETER)).replace("Hello", "Hxqzv"))
            scan(sample.root)

            assertNotEquals(before, identity.compiledEstimates(TASKS.first()), "the edit changes an estimate")
            assertEquals(emptyList(), identity.differences(TASKS))
        }
    }

    private fun scan(root: Path) {
        KrosScope.open(root).use { it.scan() }
    }

    private fun referenceWorkspace(): Path? =
        System.getProperty("kscope.fixture")?.let(Path::of)?.takeIf(Files::isDirectory)

    private fun sampleNames(): List<String> {
        val samples = Path.of(checkNotNull(javaClass.getResource("/samples")).toURI())
        return Files.list(samples).use { paths ->
            paths.filter(Files::isDirectory).map { it.fileName.toString() }.sorted().toList()
        }
    }

    private companion object {
        const val GREETER = "core/src/main/kotlin/sample/tests/Greeter.kt"

        val TASKS = listOf(
            "Make the Greeter greet in a different language and format the greeting",
            "Add a screen that lists the items and opens a detail view with a test",
            "Fix the crash when the stored value is missing and the database is empty",
            "Rename the repository class and update every caller of it",
            "Prüfe die Größe der Datei 😀 und füge einen Test hinzu",
        )

        val REFERENCE_TASKS = listOf(
            "Export the tasks to CSV with the due date of each task",
            "Show the priority of a task on the task list screen",
            "Fix the reminder that fires twice and add a test for the scheduler",
            "Move the legacy date formatting into the core module",
        )
    }
}
