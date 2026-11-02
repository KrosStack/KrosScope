package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation

/** What the static provider could not read of a convention plugin class. */
internal object ConventionDiagnostics {
    const val PARTIALLY_ANALYSED = "gradle.static.convention-plugin-partially-analysed"
    const val DESCRIPTOR_UNREADABLE = "gradle.static.plugin-descriptor-unreadable"

    fun partiallyAnalysed(plugin: PluginClass, reasons: List<String>): Diagnostic? = if (reasons.isEmpty()) {
        null
    } else {
        val parts = reasons.joinToString("; it ")
        Diagnostic(
            DiagnosticSeverity.INFO,
            ReasonCode(PARTIALLY_ANALYSED),
            "The convention plugin '${plugin.simpleName}' is only partly analysed: it $parts. " +
                "What these parts contribute to the project is not modelled.",
            SourceLocation(plugin.artifact.ref, plugin.outline.range),
        )
    }

    fun descriptorUnreadable(path: String, reason: String?) = Diagnostic(
        DiagnosticSeverity.WARNING,
        ReasonCode(DESCRIPTOR_UNREADABLE),
        "The plugin descriptor '$path' cannot be read ($reason); the plugin it registers is not modelled.",
        null,
    )
}
