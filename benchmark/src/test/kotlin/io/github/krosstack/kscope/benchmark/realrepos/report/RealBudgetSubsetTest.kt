package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunStorage
import io.github.krosstack.kscope.model.context.TokenCount
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealBudgetSubsetTest {
    @TempDir
    lateinit var output: Path

    private val subset = listOf(TokenCount(2_000), TokenCount(4_000))

    @Test
    fun `a run measured at a budget subset reports exactly those budgets and cannot pass tier one`() {
        val records = RealReportFixture.measurements(subset)

        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, records)

        assertEquals(subset, report.budgets)
        assertTrue(report.complete)
        assertTrue(report.valid)
        assertFalse(report.tier1Passed)
        assertTrue(report.groups.all { group -> group.context.map { it.budget } == subset })
        assertTrue(report.groups.all { it.measured == it.expected })
        assertTrue(report.groups.filter { it.expected > 0 }.all { it.context.all { budget -> budget.tier1Passed } })
        assertContains(RealReportMarkdown.render(report), "Context budgets in this report: 2000, 4000.")
    }

    @Test
    fun `the full budget set still passes and states all four budgets`() {
        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, RealReportFixture.measurements())

        assertEquals(ContextBenchmarkBudgets.values, report.budgets)
        assertTrue(report.tier1Passed)
        assertContains(RealReportMarkdown.render(report), "Context budgets in this report: 2000, 4000, 8000, 16000.")
    }

    @Test
    fun `records measured at different budget sets are rejected as mixed`() {
        val measured = RealReportFixture.measurements()
        val narrowed = RealReportFixture.measurement(measured.first().task, 0.2, subset)

        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, listOf(narrowed) + measured.drop(1))

        assertFalse(report.valid)
        assertFalse(report.tier1Passed)
        assertEquals(1, report.validationIssues[RealReportIssue.MIXED_BUDGETS])
        assertTrue(report.groups.none { it.valid })
    }

    @Test
    fun `a budget outside the standard set invalidates every record instead of vanishing`() {
        val records = RealReportFixture.measurements(listOf(TokenCount(3_000)))

        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, records)

        assertFalse(report.valid)
        assertEquals(records.size, report.validationIssues[RealReportIssue.INVALID_CONTEXT])
    }

    @Test
    fun `the run directory is bound to its budget set and still accepts older markers`() {
        val storage = RealRunStorage(output)
        storage.bind(RealReportFixture.manifest, IDENTITY, subset)

        storage.bind(RealReportFixture.manifest, IDENTITY, subset)
        assertFailsWith<IllegalArgumentException> { storage.bind(RealReportFixture.manifest, IDENTITY) }
        assertFailsWith<IllegalArgumentException> {
            storage.bind(RealReportFixture.manifest, IDENTITY, listOf(TokenCount(2_000)))
        }

        val marker = output.resolve(".identity")
        Files.writeString(marker, Files.readString(marker).replace(Regex(",\\s*\"budgets\":\\[[^]]*]"), ""))
        assertFailsWith<IllegalArgumentException> { storage.bind(RealReportFixture.manifest, IDENTITY, subset) }
        storage.bind(RealReportFixture.manifest, IDENTITY)
    }

    private companion object {
        val IDENTITY = "c".repeat(64)
    }
}
