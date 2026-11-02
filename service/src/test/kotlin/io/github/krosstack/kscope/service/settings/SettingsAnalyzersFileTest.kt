package io.github.krosstack.kscope.service.settings

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsAnalyzersFileTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    private fun analyzersOf(text: String): AnalyzerSettings =
        assertIs<SettingsReading.Loaded>(fixture.read(text)).settings.analyzers

    @Test
    fun `a file without an analyzers table or with an empty one switches nothing off`() {
        assertEquals(AnalyzerSettings.ALL, analyzersOf("format = 1\n"))
        assertEquals(AnalyzerSettings.ALL, analyzersOf("[analyzers]\n"))
    }

    @Test
    fun `the default file switches every group on`() {
        val text = AnalyzerKind.entries.joinToString("", "[analyzers]\n") { "${it.key} = true\n" }

        assertEquals(AnalyzerSettings.ALL, analyzersOf(text))
    }

    @Test
    fun `every key of the analyzers table is read`() {
        val text = "[analyzers]\nandroid-resources = false\naidl = false\nschemas = false\n" +
            "native-bindings = false\ngit-history = false\ncompose-resources = false\nweb-bundles = false\n"

        assertEquals(AnalyzerSettings(AnalyzerKind.entries.toSet()), analyzersOf(text))
        assertEquals(AnalyzerSettings(setOf(AnalyzerKind.AIDL)), analyzersOf("[analyzers]\naidl = false\n"))
        assertEquals(AnalyzerSettings(setOf(AnalyzerKind.SCHEMAS)), analyzersOf("analyzers.schemas = false\n"))
    }

    @Test
    fun `an unknown analyzer is refused at its line and names the valid keys`() {
        val valid = "android-resources, aidl, schemas, native-bindings, git-history, compose-resources, web-bundles"
        val message = "unknown key (valid keys: $valid)"

        fixture.assertRejected(Rejection("[analyzers]\naidl = true\nkotlin = false\n", 3, "analyzers.kotlin", message))
    }

    @Test
    fun `a switch of the wrong type is refused at its line`() {
        listOf("1", "\"yes\"", "[true]", "0.5").forEach { value ->
            val text = "format = 1\n[analyzers]\nschemas = $value\n"
            fixture.assertRejected(Rejection(text, 3, "analyzers.schemas", "must be true or false"))
        }
        fixture.assertRejected(Rejection("analyzers = true\n", 1, "analyzers", "must be a table"))
        val table = Rejection("[analyzers.aidl]\nvalue = true\n", 1, "analyzers.aidl", "must be true or false")
        fixture.assertRejected(table)
    }
}
