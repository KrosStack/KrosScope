package io.github.krosstack.kscope.service.settings

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsScopeFileTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    private fun scanOf(text: String): ScanSettings = assertIs<SettingsReading.Loaded>(fixture.read(text)).settings.scan

    private fun patterns(count: Int) = (1..count).joinToString(", ") { "\"p$it\"" }

    @Test
    fun `a file without a scan table leaves the scan as it is`() {
        assertEquals(ScanSettings(), scanOf("format = 1\n"))
        assertEquals(ScanSettings(), scanOf("[scan]\n"))
    }

    @Test
    fun `every key of the scan table is read`() {
        val text = "[scan]\ninclude = [\"app/**\", \"core\"]\nexclude = [\"**/legacy\"]\ntests = false\n" +
            "generated = [\"**/gen\"]\n"

        assertEquals(
            ScanSettings(listOf("app/**", "core"), listOf("**/legacy"), tests = false, generated = listOf("**/gen")),
            scanOf(text),
        )
    }

    @Test
    fun `a pattern outside the grammar is refused at its own line with its index`() {
        val text = "[scan]\ninclude = [\n  \"app/**\",\n  \"C:/app\",\n]\nexclude = [\"a\\\\b\", \"/x\"]\n"

        val issues = fixture.issuesOf(text)

        assertEquals(
            listOf(
                SettingsIssue(4, "scan.include[1]", "must not hold :"),
                SettingsIssue(6, "scan.exclude[0]", "must use / and not \\"),
                SettingsIssue(6, "scan.exclude[1]", "must not start with /"),
            ),
            issues,
        )
    }

    @Test
    fun `values of the wrong type are refused`() {
        val siblings = "unknown key (valid keys: include, exclude, generated, tests)"
        listOf(
            Rejection("[scan]\ninclude = \"app\"\n", 2, "scan.include", "must be an array of strings"),
            Rejection("[scan]\nexclude = 1\n", 2, "scan.exclude", "must be an array of strings"),
            Rejection("[scan]\ngenerated = [1]\n", 2, "scan.generated[0]", "must be a string"),
            Rejection("[scan]\ninclude = [[\"a\"]]\n", 2, "scan.include[0]", "must be a string"),
            Rejection("[scan]\ntests = \"no\"\n", 2, "scan.tests", "must be true or false"),
            Rejection("scan = 1\n", 1, "scan", "must be a table"),
            Rejection("[scan]\nlevel = 1\n", 2, "scan.level", siblings),
        ).forEach(fixture::assertRejected)
    }

    @Test
    fun `more than 256 patterns are refused`() {
        val message = "must have at most 256 patterns"
        val tooMany = Rejection("[scan]\ninclude = [${patterns(257)}]\n", 2, "scan.include", message)

        fixture.assertRejected(tooMany)
        assertEquals(256, scanOf("[scan]\ninclude = [${patterns(256)}]\n").include.size)
    }

    @Test
    fun `a pattern of 512 characters is allowed and one of 513 is not`() {
        val tooLong = "[scan]\nexclude = [\"${"a".repeat(513)}\"]\n"

        assertEquals(1, scanOf("[scan]\nexclude = [\"${"a".repeat(512)}\"]\n").exclude.size)
        fixture.assertRejected(Rejection(tooLong, 2, "scan.exclude[0]", "must have at most 512 characters"))
    }

    @Test
    fun `level stays refused`() {
        listOf("level").forEach {
            assertEquals(it, fixture.issuesOf("[scan]\ntests = true\n[$it]\nx = 1\n").single().key)
        }
    }
}
