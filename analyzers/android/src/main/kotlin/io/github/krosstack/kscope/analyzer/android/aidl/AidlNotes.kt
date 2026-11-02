package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlProblem
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.TextRange

/** The diagnostics about `.aidl` files, which name the file and, where they can, the position. */
internal object AidlNotes {
    fun of(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String, at: TextRange? = null) =
        Fact.OfDiagnostic(
            Diagnostic(
                severity = severity,
                code = ReasonCode("aidl.$code"),
                message = "${artifact.ref.path.value}: $message.",
                location = at?.let { SourceLocation(artifact.ref, it) },
            ),
        )

    fun parseError(artifact: Artifact, problem: AidlProblem) =
        of(artifact, DiagnosticSeverity.WARNING, "parse-error", problem.message, problem.range)

    fun skipped(artifact: Artifact, file: AidlFile): List<Fact> = file.skipped.map {
        val message = "the ${it.keyword} ${it.name.text} is not read"
        of(artifact, DiagnosticSeverity.INFO, "declaration-skipped", message, it.name.range)
    }
}
