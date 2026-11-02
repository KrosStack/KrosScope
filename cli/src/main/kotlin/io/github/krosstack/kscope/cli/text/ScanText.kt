package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.progress.ProgressLabels
import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Durations
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.service.DiagnosticEntry
import io.github.krosstack.kscope.service.ProgressStage
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.Severity
import io.github.krosstack.kscope.service.VerificationResult

/** The result of `scan`: what was indexed, what it cost, and what went wrong. */
internal object ScanText {
    private const val SLOW_STAGE_MILLIS = 100L
    private const val SHOW_TIMINGS_AFTER_MILLIS = 1_000L

    fun render(result: ScanResult, context: TextContext): String {
        val document = TextDocument(context)
        val verification = result.verification
        if (verification != null && !verification.matches) {
            document.conclusion(verificationFailure(verification), Tone.BAD)
        } else {
            document.conclusion(conclusion(result), if (result.diagnostics.errors > 0) Tone.WARNING else Tone.GOOD)
        }
        document.line().fields(summary(result, context))
        problems(result, document)
        verification?.let { verification(it, document) }
        timings(result, document)
        return document.toString()
    }

    private fun conclusion(result: ScanResult): String {
        val files = Format.noun(result.artifacts, "file")
        val duration = Durations.human(result.stageMillis.values.sum())
        val errors = result.diagnostics.errors
        val redone = result.unitsProduced
        val units = Format.count(redone + result.unitsSkipped)
        return when {
            errors > 0 -> "Scanned $files in $duration with ${Format.noun(errors, "error")}."
            redone == 0 && result.unitsRemoved == 0 -> "The index is up to date: $files, nothing changed ($duration)."
            result.unitsSkipped == 0 -> "Indexed $files in $duration."
            else -> "Updated the index: $files, ${Format.count(redone)} of $units work units redone ($duration)."
        }
    }

    private fun summary(result: ScanResult, context: TextContext): List<Pair<String, String>> {
        val diagnostics = result.diagnostics
        val units = "${Format.count(result.unitsProduced)} redone, ${Format.count(result.unitsSkipped)} reused, " +
            "${Format.count(result.unitsRemoved)} removed"
        val problems = listOf(
            Format.noun(diagnostics.errors, "error"),
            Format.noun(diagnostics.warnings, "warning"),
            Format.noun(diagnostics.infos, "note"),
        ).joinToString(", ")
        return listOf(
            "Level" to result.indexLevel,
            "Files" to Format.count(result.artifacts),
            "Work units" to units,
            "Problems" to problems,
            "Index" to context.relative(result.indexLocation),
        )
    }

    private fun problems(result: ScanResult, document: TextDocument) {
        val listed = result.diagnostics.entries.filter { it.severity != Severity.INFO }
        if (listed.isEmpty()) return
        val table = Table(listOf(Column(), Column(), Column()))
        listed.take(TextDocument.DEFAULT_LIMIT).forEach { table.row(severity(it), it.code, it.message) }
        document.section("Problems").table(table).more(listed.size - TextDocument.DEFAULT_LIMIT)
    }

    private fun severity(entry: DiagnosticEntry): String = entry.severity.name.lowercase()

    private fun verification(verification: VerificationResult, document: TextDocument) {
        document.section("Verification")
        if (verification.matches) {
            document.line("  The incremental index matches a clean rebuild.")
            return
        }
        val table = Table(listOf(Column(), Column(), Column()))
        verification.differences.take(TextDocument.DEFAULT_LIMIT).forEach {
            table.row(it.producer, it.unit, it.difference)
        }
        document.table(table).more(verification.differingUnits - TextDocument.DEFAULT_LIMIT)
    }

    private fun verificationFailure(verification: VerificationResult): String {
        val searchDocuments = verification.differingSearchDocuments
        val documents = if (searchDocuments > 0) " and ${Format.noun(searchDocuments, "search document")}" else ""
        return "Verification failed: the incremental index differs from a clean rebuild in " +
            "${Format.noun(verification.differingUnits, "work unit")}$documents."
    }

    private fun timings(result: ScanResult, document: TextDocument) {
        val slow = result.stageMillis.filterValues { it >= SLOW_STAGE_MILLIS }
        if (slow.isEmpty() || result.stageMillis.values.sum() < SHOW_TIMINGS_AFTER_MILLIS) return
        val table = Table(listOf(Column(), Column(align = Align.RIGHT)))
        slow.forEach { (stage, millis) ->
            table.row(ProgressLabels.stage(ProgressStage(stage)), Durations.human(millis))
        }
        document.section("Time by stage").table(table)
    }
}
