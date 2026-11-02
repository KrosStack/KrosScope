package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealSingleSplitReportTest {
    @Test
    fun `a manifest with one split has no group for the absent split and can pass tier one`() {
        val tasks = RealReportFixture.tasks.map { it.copy(split = RealSplit.TEST) }
        val report = RealRepositoryReports.aggregate(manifestOf(tasks), measurementsOf(tasks))

        assertEquals(listOf("a TEST", "b TEST", "null TEST"), labels(report))
        assertTrue(report.groups.all { it.complete && it.valid && it.tier1Passed })
        assertTrue(report.complete)
        assertTrue(report.valid)
        assertTrue(report.tier1Passed)
    }

    @Test
    fun `a repository with one split gets no group for the other while a repository with both keeps both`() {
        val tasks = RealReportFixture.tasks.filterNot { it.id == "a:three" }
        val report = RealRepositoryReports.aggregate(manifestOf(tasks), measurementsOf(tasks))

        assertEquals(listOf("a DEV", "b DEV", "b TEST", "null DEV", "null TEST"), labels(report))
        assertTrue(report.complete)
        assertTrue(report.tier1Passed)
    }

    private fun manifestOf(tasks: List<RealCommitTask>): RealRepositoryManifest =
        RealReportFixture.manifest.copy(tasks = tasks)

    private fun measurementsOf(tasks: List<RealCommitTask>): List<RealRepositoryMeasurement> =
        tasks.mapIndexed { index, task -> RealReportFixture.measurement(task, FULL_RECALL[index]) }

    private fun labels(report: RealRepositoryReport): List<String> =
        report.groups.map { "${it.repository} ${it.split}" }

    private companion object {
        val FULL_RECALL = listOf(0.2, 0.8, 0.4, 1.0, 0.4)
    }
}
