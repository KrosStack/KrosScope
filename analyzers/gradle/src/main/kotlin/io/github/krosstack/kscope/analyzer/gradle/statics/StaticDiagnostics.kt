package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.TextRange

private const val MAX_EXPRESSION = 80

private fun diagnostic(severity: DiagnosticSeverity, code: String, message: String, location: SourceLocation?) =
    Diagnostic(severity, ReasonCode(code), message, location)

/** What the static provider cannot know about the structure of the builds, as diagnostics. */
internal object StaticDiagnostics {
    const val DYNAMIC_INCLUDE = "gradle.static.dynamic-include"
    const val DYNAMIC_PROJECT_DIR = "gradle.static.dynamic-project-dir"
    const val CUSTOM_HIERARCHY = "gradle.static.custom-hierarchy-not-analysed"
    const val SCRIPT_NOT_ANALYSED = "gradle.static.script-not-analysed"
    const val BUILD_OUTSIDE_WORKSPACE = "gradle.static.build-outside-workspace"
    const val LAYOUT_INFERRED = "gradle.static.layout-inferred"
    const val PROJECT_NOT_FOUND = "gradle.static.project-not-found"
    const val FLAVORS_INFERRED = "gradle.static.flavors-inferred-from-directories"

    fun dynamicInclude(call: String, location: SourceLocation) = diagnostic(
        DiagnosticSeverity.WARNING,
        DYNAMIC_INCLUDE,
        "The settings script calls $call with a computed argument; projects are discovered from the directories " +
            "that hold a build script instead.",
        location,
    )

    fun dynamicProjectDir(path: String, location: SourceLocation) = diagnostic(
        DiagnosticSeverity.WARNING,
        DYNAMIC_PROJECT_DIR,
        "The directory of project '$path' is computed; the conventional directory is assumed.",
        location,
    )

    fun customHierarchy(location: SourceLocation?) = diagnostic(
        DiagnosticSeverity.WARNING,
        CUSTOM_HIERARCHY,
        "The Kotlin hierarchy is customised; the default hierarchy template is assumed and its source set edges " +
            "are heuristic.",
        location,
    )

    fun scriptNotAnalysed(artifact: Artifact) = diagnostic(
        DiagnosticSeverity.WARNING,
        SCRIPT_NOT_ANALYSED,
        "The script '${artifact.ref.path.value}' could not be outlined; what it configures is not modelled.",
        SourceLocation(artifact.ref, TextRange(1, 1, 1, 1)),
    )

    fun buildOutsideWorkspace(directory: String) = diagnostic(
        DiagnosticSeverity.WARNING,
        BUILD_OUTSIDE_WORKSPACE,
        "The included build '$directory' is outside the workspace and is not modelled.",
        null,
    )

    fun flavorsInferred(directory: String, flavors: List<String>) = diagnostic(
        DiagnosticSeverity.INFO,
        FLAVORS_INFERRED,
        "The product flavors of the project in '$directory' are computed; the source set directories " +
            "${flavors.joinToString(", ") { "'$it'" }} are taken to be flavors.",
        null,
    )

    fun layoutInferred(directory: String) = diagnostic(
        DiagnosticSeverity.INFO,
        LAYOUT_INFERRED,
        "No plugin of the project in '$directory' is known; its source sets are inferred from its directories.",
        null,
    )
}

/** What the static provider cannot know about what the scripts configure, as diagnostics. */
internal object ScriptDiagnostics {
    const val PLUGIN_NOT_ANALYSED = "gradle.static.plugin-not-analysed"
    const val ALIAS_UNRESOLVED = "gradle.static.catalog-alias-unresolved"
    const val DEPENDENCY_NOT_ANALYSED = "gradle.static.dependency-not-analysed"
    const val VALUE_NOT_ANALYSED = "gradle.static.value-not-analysed"
    const val CROSS_PROJECT_CONFIGURATION = "gradle.static.cross-project-configuration-not-analysed"
    const val PUBLICATION_NOT_ANALYSED = "build.publication.not-analysed"

    fun pluginNotAnalysed(id: String, location: SourceLocation?) = diagnostic(
        DiagnosticSeverity.INFO,
        PLUGIN_NOT_ANALYSED,
        "Plugin '$id' is applied; the plugins it applies and what it contributes to the build are not analysed.",
        location,
    )

    fun aliasUnresolved(catalog: String, accessor: String, location: SourceLocation) = diagnostic(
        DiagnosticSeverity.WARNING,
        ALIAS_UNRESOLVED,
        "The version catalog '$catalog' has no entry '$accessor'.",
        location,
    )

    fun dependencyNotAnalysed(expression: String, location: SourceLocation) = diagnostic(
        DiagnosticSeverity.INFO,
        DEPENDENCY_NOT_ANALYSED,
        "The dependency notation '${expression.take(MAX_EXPRESSION)}' cannot be resolved statically.",
        location,
    )

    fun valueNotAnalysed(property: String, expression: String, location: SourceLocation) = diagnostic(
        DiagnosticSeverity.INFO,
        VALUE_NOT_ANALYSED,
        "The value of '$property' is computed ('${expression.take(MAX_EXPRESSION)}') and is not modelled.",
        location,
    )

    fun crossProjectConfiguration(block: String, location: SourceLocation) = diagnostic(
        DiagnosticSeverity.INFO,
        CROSS_PROJECT_CONFIGURATION,
        "The '$block' block configures other projects; what it applies to them is not modelled.",
        location,
    )

    fun publicationNotAnalysed(path: String, reasons: List<String>) = diagnostic(
        DiagnosticSeverity.WARNING,
        PUBLICATION_NOT_ANALYSED,
        "The module '$path' publishes, but its coordinates are not modelled: ${reasons.joinToString("; ")}.",
        null,
    )

    fun publicationVersionNotRead(path: String, reasons: List<String>) = diagnostic(
        DiagnosticSeverity.INFO,
        PUBLICATION_NOT_ANALYSED,
        "The module '$path' publishes, but ${reasons.joinToString("; ")}; its publication is recorded without a " +
            "version and linked by group and artifact only.",
        null,
    )
}
