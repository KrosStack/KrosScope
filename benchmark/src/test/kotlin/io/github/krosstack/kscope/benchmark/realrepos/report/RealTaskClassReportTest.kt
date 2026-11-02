package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealTaskClassReportTest {
    private val levels = listOf(1 to false, 1 to true, 4 to false, 2 to false, 4 to true)
    private val full = listOf(0.2, 0.8, 0.4, 1.0, 0.4)
    private val tasks = RealReportFixture.tasks.mapIndexed { index, task ->
        task.copy(level = levels[index].first, named = levels[index].second)
    }
    private val manifest = RealReportFixture.manifest.copy(tasks = tasks)
    private val records = tasks.mapIndexed { index, task -> RealReportFixture.measurement(task, full[index]) }

    @Test
    fun `results are grouped by level and by named per split`() {
        val report = RealRepositoryReports.aggregate(manifest, records)
        val labels = report.classGroups.map { "${it.taskClass?.label} ${it.split} ${it.expected}" }

        assertEquals(
            listOf(
                "Level 1 DEV 2",
                "Level 2 DEV 1",
                "Level 4 TEST 2",
                "Named DEV 1",
                "Named TEST 1",
                "Not named DEV 2",
                "Not named TEST 1",
            ),
            labels,
        )
        assertTrue(report.classGroups.all { it.valid && it.measured == it.expected })
        val levelOne = report.classGroups.first()
        assertEquals(0.5, levelOne.localization.methods.getValue(BenchmarkMethod.FULL)?.recall5)
        assertTrue(report.tier1Passed)
    }

    @Test
    fun `the markdown report holds one table row per class`() {
        val markdown = RealReportMarkdown.render(RealRepositoryReports.aggregate(manifest, records))

        assertTrue(markdown.contains("# Results by task class"))
        assertTrue(markdown.contains("| Level 4 | TEST | 2 | 2 | "))
        assertTrue(markdown.contains("| Not named | DEV | 2 | 2 | "))
        assertTrue(markdown.contains("## Level 2 / DEV"))
    }

    @Test
    fun `a manifest without levels reports no classes and its encoding is unchanged`() {
        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, RealReportFixture.measurements())
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        assertTrue(report.classGroups.isEmpty())
        assertNull(report.groups.first().taskClass)
        assertFalse(json.encodeToString(report).contains("classGroups"))
        assertFalse(json.encodeToString(report).contains("taskClass"))
        assertFalse(RealReportMarkdown.render(report).contains("task class"))
        val encoded = json.encodeToString(RealCommitTask.serializer(), RealReportFixture.tasks.first())
        assertFalse(encoded.contains("level") || encoded.contains("named") || encoded.contains("workspace"))
        assertEquals(RealSplit.DEV, RealReportFixture.tasks.first().split)
    }
}
