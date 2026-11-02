package io.github.krosstack.kscope.service.settings.level

import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.settings.Rejection
import io.github.krosstack.kscope.service.settings.SettingsFileFixture
import io.github.krosstack.kscope.service.settings.SettingsReading
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsLevelTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    private fun levelOf(text: String) = assertIs<SettingsReading.Loaded>(fixture.read(text)).settings.level

    @Test
    fun `the level is read from the top level key`() {
        assertEquals(ScanLevel.RESOLVED, levelOf("level = \"resolved\"\n"))
        assertEquals(ScanLevel.EXACT, levelOf("format = 1\nlevel = \"exact\"\n\n[scan]\ntests = false\n"))
    }

    @Test
    fun `a file without the key asks for no level`() {
        assertEquals(null, levelOf("format = 1\n"))
    }

    @Test
    fun `a level that is not a level or not a string is refused`() {
        val choices = "(valid values: syntax, resolved, exact)"
        fixture.assertRejected(Rejection("level = \"deep\"\n", 1, "level", "must be one of the levels $choices"))
        fixture.assertRejected(Rejection("level = \"Exact\"\n", 1, "level", "must be one of the levels $choices"))
        fixture.assertRejected(Rejection("level = 1\n", 1, "level", "must be a string"))
        fixture.assertRejected(Rejection("level = true\n", 1, "level", "must be a string"))
        fixture.assertRejected(Rejection("[level]\nvalue = 1\n", 1, "level", "must be a string"))
    }
}
