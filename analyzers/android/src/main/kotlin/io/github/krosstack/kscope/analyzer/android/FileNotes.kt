package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.TextRange

private val FILE_START = TextRange(1, 1, 1, 1)

/** A note about the file as a whole, shown at its first character. */
internal fun Artifact.note(severity: DiagnosticSeverity, code: String, message: String): Fact = Fact.OfDiagnostic(
    Diagnostic(severity, ReasonCode(code), "${ref.path.value}: $message.", SourceLocation(ref, FILE_START)),
)

/** A note about a module as a whole. */
internal fun Module.note(severity: DiagnosticSeverity, code: String, message: String): Fact =
    Fact.OfDiagnostic(Diagnostic(severity, ReasonCode(code), "$path: $message.", null))
