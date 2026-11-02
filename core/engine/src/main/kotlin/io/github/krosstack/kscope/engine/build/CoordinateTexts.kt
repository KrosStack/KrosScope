package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation

internal data class BuiltFromSource(val module: String, val version: String)

/** A dependency on a coordinate: the consumer by workspace directory and Gradle path, and where it is declared. */
internal data class Finding(val consumer: String, val coordinate: String, val at: SourceLocation?)

/** The diagnostics of coordinate resolution. */
internal object CoordinateTexts {
    private const val HEURISTIC = "References across this dependency are heuristic."
    private const val UNSPECIFIED = "unspecified"

    fun mismatch(finding: Finding, pinned: String, built: BuiltFromSource) = Diagnostic(
        DiagnosticSeverity.WARNING,
        ReasonCode("workspace.coordinate-version-mismatch"),
        "${finding.consumer} pins ${finding.coordinate}:$pinned, but ${built.module} builds ${built.version} " +
            "from source. $HEURISTIC",
        finding.at,
    )

    fun unknown(finding: Finding, pinned: String?, built: BuiltFromSource) = Diagnostic(
        DiagnosticSeverity.INFO,
        ReasonCode("workspace.coordinate-version-unknown"),
        "${finding.consumer} pins ${finding.coordinate} at ${pinned ?: "no fixed version"}, " +
            "${comparison(built)} $HEURISTIC",
        finding.at,
    )

    private fun comparison(built: BuiltFromSource) = if (built.version == UNSPECIFIED) {
        "but the version that ${built.module} builds from source is not read."
    } else {
        "which cannot be compared with ${built.version} that ${built.module} builds from source."
    }

    fun ambiguous(finding: Finding, producers: List<String>) = Diagnostic(
        DiagnosticSeverity.WARNING,
        ReasonCode("workspace.coordinate-ambiguous"),
        "${finding.consumer} depends on ${finding.coordinate}, which ${producers.joinToString(", ")} all publish. " +
            "The dependency is not linked to any of them.",
        finding.at,
    )
}
