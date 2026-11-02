package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Platform

internal class TargetDraft(val name: String, val platform: Platform)

internal class VariantDraft(val name: String, val buildType: String, val flavors: List<String>)

/** A source set of a layout; [targets] and [variants] are names, and [declared] says that a script creates it. */
internal class SourceSetDraft(
    val name: String,
    val dependsOn: List<String>,
    val targets: List<String>,
    val variants: List<String>,
    val declared: Boolean,
)

/**
 * The targets, variants and source sets a project has, before they are given identities. [inferred] says that the
 * source sets were found from the directories alone, because no plugin of the project is known.
 */
internal class Layout(
    val targets: List<TargetDraft>,
    val variants: List<VariantDraft>,
    val sourceSets: List<SourceSetDraft>,
    val problems: List<Diagnostic> = emptyList(),
    val inferred: Boolean = false,
) {
    companion object {
        val empty = Layout(emptyList(), emptyList(), emptyList())
    }
}

internal fun String.upperFirst(): String = replaceFirstChar { it.uppercase() }
