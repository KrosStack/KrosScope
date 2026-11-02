package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.fileNote
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact

private const val PREFIX = "web"

/** The notes about files that the analyzer could not use, each located at the start of its file. */
internal object WebNotes {
    fun unreadable(artifact: Artifact): Fact =
        note(artifact, DiagnosticSeverity.WARNING, "unreadable", "it could not be read")

    fun moduleUnknown(artifact: Artifact): Fact =
        note(artifact, DiagnosticSeverity.INFO, "module-unknown", "no module contains it")

    fun invalidManifest(artifact: Artifact): Fact =
        note(artifact, DiagnosticSeverity.INFO, "invalid-manifest", "it is no JSON object")

    fun unreadableSourceMap(artifact: Artifact): Fact =
        note(artifact, DiagnosticSeverity.INFO, "unreadable-source-map", "it could not be read")

    fun invalidSourceMap(artifact: Artifact): Fact =
        note(artifact, DiagnosticSeverity.INFO, "invalid-source-map", "it is no source map with a list of sources")

    fun inlineSourceMap(artifact: Artifact): Fact = note(
        artifact,
        DiagnosticSeverity.INFO,
        "inline-source-map",
        "its source map is inline, and inline maps are not read",
    )

    private fun note(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String): Fact =
        fileNote(artifact, severity, "$PREFIX.$code", message)
}
