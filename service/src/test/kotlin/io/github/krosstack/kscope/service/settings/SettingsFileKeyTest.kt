package io.github.krosstack.kscope.service.settings

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsFileKeyTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `an unknown key or table is refused and names its valid siblings`() {
        val top = "unknown key (valid keys: format, level, resources, scan, analyzers, freshness)"
        val nested = "unknown key (valid keys: workers, worker-heap-mib)"
        listOf(
            Rejection("colour = 1\n", 1, "colour", top),
            Rejection("format = 1\n\n[colour]\nred = 1\n", 3, "colour", top),
            Rejection("[colour.red]\nvalue = 1\n", 1, "colour", top),
            Rejection("[resources]\nworker = 2\n", 2, "resources.worker", nested),
            Rejection("[resources.limits]\nworkers = 2\n", 1, "resources.limits", nested),
            Rejection("[resources]\nworkers = 2\nheap = 512\n", 3, "resources.heap", nested),
        ).forEach(fixture::assertRejected)
    }

    @Test
    fun `a key written twice is refused at the second line and names the key`() {
        val once = fixture.issuesOf("format = 1\nformat = 1\n")
        assertEquals(listOf(SettingsIssue(2, "format", "format previously defined at line 1, column 1")), once)

        val twice = fixture.issuesOf("[resources]\nworkers = 1\n[resources]\nworkers = 2\n")
        assertEquals(listOf(3, 4), twice.map { it.line })
        assertEquals(listOf("resources", "workers"), twice.map { it.key })
    }

    @Test
    fun `broken syntax is refused with its line`() {
        val issues = fixture.issuesOf("format =\n[resources\nworkers = 4\n")

        assertEquals(listOf(1, 2), issues.map { it.line })
        assertTrue(issues.all { it.message.isNotBlank() }, "$issues")
        assertEquals("kscope.toml:1: ${issues.first().message}", issues.first().describe("kscope.toml"))
    }

    @Test
    fun `a number too large for an integer is refused`() {
        val issue = fixture.issuesOf("[resources]\nworkers = 99999999999999999999\n").single()

        assertEquals(2, issue.line)
    }
}
