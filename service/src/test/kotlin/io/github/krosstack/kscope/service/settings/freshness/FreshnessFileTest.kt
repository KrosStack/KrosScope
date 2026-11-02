package io.github.krosstack.kscope.service.settings.freshness

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.settings.FreshnessReading
import io.github.krosstack.kscope.service.settings.FreshnessSettings
import io.github.krosstack.kscope.service.settings.Rejection
import io.github.krosstack.kscope.service.settings.ResourceSettings
import io.github.krosstack.kscope.service.settings.SettingsFileFixture
import io.github.krosstack.kscope.service.settings.SettingsReading
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreshnessFileTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    private fun freshnessOf(text: String): FreshnessReading {
        fixture.read(text)
        return KrosScope.freshnessOf(fixture.directory)
    }

    @Test
    fun `no file and no section mean watching with three seconds of quiet`() {
        assertEquals(FreshnessReading(), KrosScope.freshnessOf(fixture.directory))
        assertEquals(FreshnessReading(), freshnessOf("[resources]\nworkers = 2\n"))
        assertEquals(FreshnessSettings(true, 3), freshnessOf("[freshness]\n").settings)
    }

    @Test
    fun `both keys are read and accepted at their bounds`() {
        val text = "[freshness]\nwatch = false\nquiet-seconds = 1\n"
        assertEquals(FreshnessSettings(false, 1), freshnessOf(text).settings)
        assertEquals(FreshnessSettings(true, 300), freshnessOf("freshness.quiet-seconds = 300\n").settings)
        assertEquals(FreshnessSettings(false, 3), freshnessOf("[freshness]\nwatch = false\n").settings)
    }

    @Test
    fun `the section lands in the settings of the file`() {
        val reading = fixture.read("[freshness]\nwatch = false\nquiet-seconds = 9\n[resources]\nworkers = 2\n")

        val settings = (reading as SettingsReading.Loaded).settings
        assertEquals(FreshnessSettings(false, 9), settings.freshness)
        assertEquals(ResourceSettings(workers = 2), settings.resources)
    }

    @Test
    fun `wrong values and keys are refused with their line and key`() {
        val unknown = "unknown key (valid keys: quiet-seconds, watch)"
        val range = "must be between 1 and 300"
        listOf(
            Rejection("[freshness]\nquiet-seconds = 0\n", 2, "freshness.quiet-seconds", range),
            Rejection("[freshness]\nquiet-seconds = 301\n", 2, "freshness.quiet-seconds", range),
            Rejection("[freshness]\nquiet-seconds = \"3\"\n", 2, "freshness.quiet-seconds", "must be an integer"),
            Rejection("[freshness]\nwatch = 1\n", 2, "freshness.watch", "must be true or false"),
            Rejection("[freshness]\nwatching = true\n", 2, "freshness.watching", unknown),
            Rejection("freshness = 1\n", 1, "freshness", "must be a table"),
        ).forEach(fixture::assertRejected)
    }

    @Test
    fun `an invalid file is reported with defaults and a valid one is not`() {
        val invalid = freshnessOf("[freshness]\nquiet-seconds = 0\n")

        assertFalse(invalid.valid)
        assertEquals(FreshnessSettings(), invalid.settings)
        assertEquals(listOf(2), invalid.issues.map { it.line })
        assertTrue(freshnessOf("[freshness]\nwatch = false\n").valid)
    }
}
