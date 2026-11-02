package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.EncodingReport
import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.StreamEncoding
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.format.Emphasis
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.service.DiagnosticEntry
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.DifferenceEntry
import io.github.krosstack.kscope.service.DoctorCheck
import io.github.krosstack.kscope.service.DoctorResult
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.Severity
import io.github.krosstack.kscope.service.VerificationResult
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RendererGoldenTest {
    private val workspace: Path = Path.of("workspace").toAbsolutePath()
    private val context = TextContext(workspace, Emphasis.NONE)
    private val indexLocation = workspace.resolve(".kscope").toString().replace('\\', '/')
    private val clean = DiagnosticSummary(0, 0, 5, emptyList())

    private fun scan(
        produced: Int,
        skipped: Int,
        diagnostics: DiagnosticSummary,
        verification: VerificationResult? = null,
        stageMillis: Map<String, Long> = emptyMap(),
    ) = ScanResult(154, produced, skipped, 0, diagnostics, verification, stageMillis, indexLocation)

    private fun render(result: ScanResult): String = ScanText.render(result, context)

    @Test
    fun `a first scan names its files its work and where the time went`() {
        val stages = linkedMapOf(
            "discovery" to 283L,
            "build-model" to 2_040L,
            "synthesis" to 266L,
            "production" to 6_950L,
            "linking" to 146L,
        )

        Golden.assertMatches("renderer-scan-first", render(scan(103, 0, clean, stageMillis = stages)))
    }

    @Test
    fun `a scan with nothing to do says that the index is up to date`() {
        val result = scan(0, 103, clean, stageMillis = mapOf("discovery" to 120L))

        Golden.assertMatches("renderer-scan-unchanged", render(result))
    }

    @Test
    fun `an incremental scan says how much of the work was redone`() {
        val result = scan(24, 76, clean, stageMillis = mapOf("production" to 3_400L))

        Golden.assertMatches("renderer-scan-incremental", render(result))
    }

    @Test
    fun `errors and warnings are listed and long lists are cut`() {
        val worker = DiagnosticEntry(Severity.ERROR, "kotlin.worker-failed", "The Kotlin worker failed: out of memory")
        val aliases = (1..12).map {
            DiagnosticEntry(Severity.WARNING, "gradle.catalog.alias-$it", "The alias $it is declared twice")
        }
        val diagnostics = DiagnosticSummary(1, 12, 5, listOf(worker) + aliases)

        val result = scan(103, 0, diagnostics, stageMillis = mapOf("production" to 9_000L))

        Golden.assertMatches("renderer-scan-problems", render(result))
    }

    @Test
    fun `a failed verification comes first and shows the differing units`() {
        val differences = (1..12).map {
            DifferenceEntry("kscope.analyzer.kotlin", "source-set :lib@main$it", "different-content")
        }
        val verification = VerificationResult(false, 12, differences, differingSearchDocuments = 2)

        Golden.assertMatches("renderer-scan-verification", render(scan(0, 103, clean, verification)))
    }

    @Test
    fun `the doctor report says what is missing and how to fix it`() {
        val checks = listOf(
            DoctorCheck("jdk", true, "Java 17; JDK 17 or newer required"),
            DoctorCheck("worker", false, "Kotlin worker classpath or installation"),
            DoctorCheck("git", true, "Git executable"),
            DoctorCheck("index", false, "Workspace index"),
            DoctorCheck("android-sdk", false, "Android SDK directory"),
        )
        val result = DoctorResult(checks, checks.filterNot { it.available }.map { "${it.name}: unavailable" })
        val encodings = EncodingReport(
            StreamEncoding(charset("Cp850"), true),
            StreamEncoding(Charsets.UTF_8, false),
            Charsets.UTF_8,
            "Cp1252",
        )

        Golden.assertMatches("renderer-doctor", DoctorText.render(result, encodings, context))
    }

    @Test
    fun `a doctor report with everything available is short`() {
        val checks = listOf("jdk", "worker", "git", "index", "android-sdk").map { DoctorCheck(it, true, "detail") }

        val text = DoctorText.render(DoctorResult(checks, emptyList()), EncodingReport.DETACHED, context)

        assertEquals("All 5 checks passed.", text.lineSequence().first())
        assertFalse(text.contains("What to do"), text)
    }
}
