package io.github.krosstack.kscope.benchmark

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentDefaultsTest {
    private val workingDirectory: Path = Path.of("").toAbsolutePath()

    private fun withProperties(gold: String?, fixture: String?, action: () -> Unit) =
        ScopedSystemProperties.with(mapOf("kscope.gold" to gold, "kscope.fixture" to fixture), action)

    @Test
    fun `without the properties the defaults are absolute paths below the working directory`() {
        withProperties(gold = null, fixture = null) {
            assertEquals(workingDirectory.resolve("benchmark/reference/tasks"), Path.of(AgentDefaults.tasks()))
            assertEquals(workingDirectory.resolve("fixtures/reference-workspace"), Path.of(AgentDefaults.workspace()))
        }
    }

    @Test
    fun `relative property values are made absolute against the working directory and normalised`() {
        withProperties(gold = "elsewhere/../gold", fixture = "./fixture") {
            assertEquals(workingDirectory.resolve("gold/tasks"), Path.of(AgentDefaults.tasks()))
            assertEquals(workingDirectory.resolve("fixture"), Path.of(AgentDefaults.workspace()))
        }
    }

    @Test
    fun `absolute property values give the paths below them`() {
        val gold = workingDirectory.resolve("gold-directory")
        val fixture = workingDirectory.resolve("fixture-directory")

        withProperties(gold = gold.toString(), fixture = fixture.toString()) {
            assertEquals(gold.resolve("tasks"), Path.of(AgentDefaults.tasks()))
            assertEquals(fixture, Path.of(AgentDefaults.workspace()))
        }
    }
}
