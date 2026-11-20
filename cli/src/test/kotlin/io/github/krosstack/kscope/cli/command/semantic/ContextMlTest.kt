package io.github.krosstack.kscope.cli.command.semantic

import io.github.krosstack.kscope.cli.serve.semantic.SemanticQueryFixture
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContextMlTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `root no ml preserves structural JSON and Markdown with an installed query provider`() {
        val fixture = SemanticQueryFixture(directory)
        for (format in listOf("json", "markdown")) {
            val arguments = fixture.context + listOf("--format", format)
            val on = fixture.local(arguments)
            assertEquals(0, on.statusCode, on.output)
            assertTrue(fixture.fake.queriesEmbedded > 0)
            assertNotEquals(fixture.structural(format), on.stdout)
            val queries = fixture.fake.queriesEmbedded
            val offCommands = listOf(
                listOf("--no-ml") + arguments,
                listOf("--ml", "off") + arguments,
                listOf("--no-ml") + arguments + listOf("--ml", "on"),
                arguments + listOf("--no-ml", "--stage", "lexical"),
            )
            for (command in offCommands) {
                val off = fixture.local(command)
                assertEquals(0, off.statusCode, off.output)
                assertEquals(fixture.structural(format), off.stdout)
                assertEquals(queries, fixture.fake.queriesEmbedded)
            }
            assertEquals(on.stdout, fixture.local(arguments + listOf("--stage", "lexical")).stdout)
        }
        assertEquals(fixture.fake.workersStarted, fixture.fake.workersClosed)
    }
}
