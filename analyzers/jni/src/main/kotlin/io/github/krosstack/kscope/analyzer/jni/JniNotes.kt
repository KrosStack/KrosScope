package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ReasonCode
import java.io.IOException

/** The notes that the producers of this module leave for a file they could not make symbols of. */
internal object JniNotes {
    fun moduleUnknown(artifact: Artifact): Fact = note(artifact, "module-unknown", "no module contains it")

    fun unreadable(artifact: Artifact, failure: IOException): Fact =
        note(artifact, "unreadable", "it could not be read: ${failure.message}")

    private fun note(artifact: Artifact, code: String, message: String) = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.INFO, ReasonCode("jni.$code"), "${artifact.ref.path.value}: $message.", null),
    )
}
