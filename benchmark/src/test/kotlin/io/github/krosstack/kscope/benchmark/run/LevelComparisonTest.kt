package io.github.krosstack.kscope.benchmark.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelComparisonTest {
    @Test
    fun `modes are found by their label and by the name of their level`() {
        assertEquals(ScanMode.SYNTAX, ScanMode.parse("syntax"))
        assertEquals(ScanMode.DEFAULT, ScanMode.parse("default"))
        assertEquals(ScanMode.DEFAULT, ScanMode.parse("resolved"))
        assertEquals(ScanMode.TRUSTED, ScanMode.parse("trusted"))
        assertEquals(ScanMode.TRUSTED, ScanMode.parse("exact"))
        assertEquals(null, ScanMode.parse("sideways"))
    }

    @Test
    fun `the comparison lists the levels from the shallowest and every class once`() {
        val comparison = LevelComparison(listOf(ConformanceFixture.default, ConformanceFixture.syntax))
        val data = comparison.data()

        assertEquals(listOf("syntax", "resolved"), data.levels.map { it.level })
        assertEquals(data.classes.map { it.name }.distinct(), data.classes.map { it.name })
        assertTrue(data.classes.all { it.levels.size == 2 })
        assertEquals(0, data.levels.first().compilerRelationships)
    }

    @Test
    fun `apart from the timings the comparison is the same on every render`() {
        val results = listOf(ConformanceFixture.default, ConformanceFixture.syntax)

        assertEquals(LevelComparison(results).render(), LevelComparison(results.reversed()).render())
    }
}
