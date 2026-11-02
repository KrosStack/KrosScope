package io.github.krosstack.kscope.benchmark.localization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationStatementsTest {
    @Test
    fun `statement loader accepts a file or directory containing statements without reading companion metadata`() {
        val directory = Files.createTempDirectory("kscope-localization-statements")
        try {
            val first = LocalizationStatement("a", "First paraphrase")
            val second = LocalizationStatement("b", "Second paraphrase")
            val combined = directory.resolve("statements.json")
            Files.writeString(combined, Json.encodeToString(listOf(second, first)))
            assertEquals(listOf(first, second), LocalizationBenchmark.loadStatements(combined))

            Files.writeString(directory.resolve("README.md"), "The frozen statements are independent inputs.")
            Files.writeString(directory.resolve("freeze.json"), "{}")
            assertEquals(listOf(first, second), LocalizationBenchmark.loadStatements(directory))

            val single = directory.resolve("single")
            Files.writeString(single, Json.encodeToString(first))
            assertEquals(listOf(first), LocalizationBenchmark.loadStatements(single))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `invalid held out pairing remains a reportable corpus failure`() {
        val tasks = listOf(LocalizationTask("original", "statement", listOf("source")))
        val invalid = listOf(LocalizationStatement("extra", "another statement"))
        val errors = LocalizationStatements.validationErrors(tasks, invalid)
        val suite = LocalizationBenchmark.combine(emptyList(), validationErrors = errors)

        assertTrue(errors.isNotEmpty())
        assertFalse(suite.valid)
        assertFalse(suite.complete)
        assertFalse(suite.gatePassed)
        assertTrue(LocalizationReport.markdown(suite).contains("Validation error:"))
    }
}
