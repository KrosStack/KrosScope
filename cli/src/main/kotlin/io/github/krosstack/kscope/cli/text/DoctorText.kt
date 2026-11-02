package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.EncodingReport
import io.github.krosstack.kscope.cli.StreamEncoding
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.service.DoctorCheck
import io.github.krosstack.kscope.service.DoctorResult

/** The result of `doctor`: which tools are available, and which encodings this run uses. */
internal object DoctorText {
    private val remedies = mapOf(
        "jdk" to "Run kscope with JDK 17 or newer.",
        "worker" to "Set KSCOPE_HOME to the kscope installation directory, or reinstall kscope.",
        "git" to "Install Git; without it a scan reads no history and does not honour .gitignore.",
        "index" to "Run `kscope scan` to build it.",
        "android-sdk" to "Optional. Set ANDROID_HOME to the Android SDK to analyse Android modules against it.",
    )

    private val findings = mapOf(
        "worker" to ("Kotlin analysis worker found" to "Kotlin analysis worker not found"),
        "git" to ("Git found" to "Git not found"),
        "index" to ("Index found in this workspace" to "No index in this workspace"),
        "android-sdk" to ("Android SDK directory found" to "Android SDK directory not found"),
    )

    fun render(result: DoctorResult, encodings: EncodingReport, context: TextContext): String {
        val document = TextDocument(context)
        val missing = result.checks.filterNot { it.available }
        document.conclusion(conclusion(result, missing), if (missing.isEmpty()) Tone.GOOD else Tone.WARNING)
        document.line().table(checks(result.checks))
        if (missing.isNotEmpty()) document.section("What to do").items(missing.map(::remedy))
        document.section("Encodings").fields(encodingFields(encodings))
        if (!encodings.argumentsAreUnicode) document.hint(ARGUMENT_NOTE)
        return document.toString()
    }

    private fun conclusion(result: DoctorResult, missing: List<DoctorCheck>): String = when {
        missing.isEmpty() -> "All ${result.checks.size} checks passed."
        else -> "${result.checks.size - missing.size} of ${result.checks.size} checks passed."
    }

    private fun checks(checks: List<DoctorCheck>): Table {
        val table = Table(listOf(Column(), Column(), Column()))
        checks.forEach { table.row(if (it.available) "ok" else "missing", it.name, describe(it)) }
        return table
    }

    private fun describe(check: DoctorCheck): String {
        val wording = findings[check.name] ?: return check.detail
        return if (check.available) wording.first else wording.second
    }

    private fun remedy(check: DoctorCheck): String = "${check.name}: ${remedies[check.name] ?: check.detail}"

    private fun encodingFields(encodings: EncodingReport): List<Pair<String, String>> = listOf(
        "Standard output" to stream(encodings.stdout),
        "Standard error" to stream(encodings.stderr),
        "Default charset" to encodings.defaultCharset.displayName(),
        "Arguments" to encodings.arguments,
    )

    private fun stream(encoding: StreamEncoding): String {
        val name = encoding.charset.displayName()
        return if (encoding.isConsole) "$name (console)" else "$name (redirected or captured)"
    }

    private const val ARGUMENT_NOTE =
        "Non-ASCII characters in command-line arguments can be replaced before kscope sees them\n" +
            "(a limit of JDK 17 on Windows). Pass such text with --task-file, which is read as UTF-8."
}
