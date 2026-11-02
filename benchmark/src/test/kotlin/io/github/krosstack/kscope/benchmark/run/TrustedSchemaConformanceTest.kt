package io.github.krosstack.kscope.benchmark.run

import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same with the classpaths of the build, where the chains of the query library resolve as well. */
@Tag("gradle-integration")
class TrustedSchemaConformanceTest {
    private val result = ConformanceFixture.trusted

    @Test
    fun `the Wire and SQLDelight edges of the closed-world files are found completely`() {
        val files = result.closedWorld.files.associateBy { it.file.substringAfterLast('/') }
        listOf("TaskDtoMapper.kt", "TaskRepository.kt").forEach { name ->
            val file = files.getValue(name)
            assertEquals(1.0, file.score.recall, "$name ${file.score.misses}")
            assertEquals(1.0, file.score.precision, "$name ${file.score.extras}")
        }
    }

    @Test
    fun `the classes of the schema artifacts are found with the classpath as well`() {
        SchemaConformance.CLASSES.forEach { name ->
            val entry = SchemaConformance.classOf(result, name)
            assertEquals(1.0, entry.score?.recall, "$name ${entry.score?.misses}")
        }
        assertEquals(0, result.coverage.integrity.dangling, "${result.coverage.integrity.examples}")
    }
}
