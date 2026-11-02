package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.run.ScanMode
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformanceOptionsTest {
    private val workingDirectory: Path = Path.of("").toAbsolutePath()

    private fun parse(extra: List<String>, gold: String? = null, fixture: String? = null): ConformanceOptions =
        ScopedSystemProperties.with(mapOf("kscope.gold" to gold, "kscope.fixture" to fixture)) {
            checkNotNull(ConformanceOptions.parse(listOf("conformance", "--mode", "default", "--out", "out") + extra))
        }

    @Test
    fun `relative workspace and gold flags are made absolute against the working directory and normalised`() {
        val options = parse(listOf("--workspace", "elsewhere/../workspace", "--gold", "./gold"))

        assertEquals(workingDirectory.resolve("workspace"), options.workspace)
        assertEquals(workingDirectory.resolve("gold"), options.gold)
    }

    @Test
    fun `without flags and properties the defaults are absolute paths below the working directory`() {
        val options = parse(emptyList())

        assertEquals(workingDirectory.resolve("fixtures/reference-workspace"), options.workspace)
        assertEquals(workingDirectory.resolve("benchmark/reference"), options.gold)
    }

    @Test
    fun `relative property values are made absolute against the working directory and normalised`() {
        val options = parse(emptyList(), gold = "elsewhere/../gold", fixture = "./fixture")

        assertEquals(workingDirectory.resolve("fixture"), options.workspace)
        assertEquals(workingDirectory.resolve("gold"), options.gold)
    }

    @Test
    fun `absolute property values are kept`() {
        val gold = workingDirectory.resolve("gold-directory")
        val fixture = workingDirectory.resolve("fixture-directory")

        val options = parse(emptyList(), gold = gold.toString(), fixture = fixture.toString())

        assertEquals(fixture, options.workspace)
        assertEquals(gold, options.gold)
    }

    @Test
    fun `a level or a mode names the scans to run and all names every level`() {
        fun modes(vararg flags: String) =
            checkNotNull(ConformanceOptions.parse(listOf("conformance") + flags + listOf("--out", "out"))).modes

        assertEquals(listOf(ScanMode.SYNTAX), modes("--level", "syntax"))
        assertEquals(listOf(ScanMode.DEFAULT), modes("--level", "resolved"))
        assertEquals(listOf(ScanMode.TRUSTED), modes("--mode", "trusted"))
        assertEquals(ScanMode.entries.toList(), modes("--level", "all"))
    }
}
