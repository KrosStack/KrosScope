package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.cli.text.format.RelevanceCutoff
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.service.ArtifactKind

/**
 * The result of `analyze` for a task: which files it touches, in what scope, and what is left uncertain. Lists show
 * the files that pass the [RelevanceCutoff]; binary files are only counted and documents are listed apart.
 */
internal object AnalyzeText {
    private const val TASK_WIDTH = 100
    private const val TERMS_SHOWN = 12
    private const val NEXT_STEP = "Next: kscope context \"<task>\" --budget 8000 compiles the source of these files."
    private const val BRIEF_NEXT_STEP =
        "Next: kscope context \"<task>\" --budget 4000 --format markdown compiles the source of these files."

    /** The sections `--sections` accepts for a task analysis, in the order they are printed. */
    val SECTIONS: List<String> = listOf(
        Section.HEADLINE, Section.TOP, Section.QUERY, Section.EDIT, Section.READ, Section.DOCUMENTS, Section.CUTOFF,
        Section.TESTS, Section.SCOPE, Section.GAPS, Section.CONNECTEDNESS, Section.CONTEXT, Section.NEXT,
    )
    private val BRIEF = setOf(Section.HEADLINE, Section.TOP, Section.TESTS, Section.NEXT)
    private val STANDARD = SECTIONS.toSet() - Section.TOP

    /** The files of [result] that pass the cut-off, whose kinds [render] needs. */
    fun relevantFiles(result: AnalysisResult): List<ArtifactRef> =
        RelevanceCutoff.select(result.files) { it.score }.relevant.map { it.artifact }

    fun render(
        result: AnalysisResult,
        context: TextContext,
        kinds: Map<ArtifactRef, ArtifactKind> = emptyMap(),
        reading: String? = null,
    ): String {
        val document = TextDocument(context, context.shown { if (it == DetailLevel.BRIEF) BRIEF else STANDARD })
        reading?.let { document.note(it) }
        if (result.files.isEmpty()) return nothingFound(result, document)
        val listing = Listing.of(result, kinds)
        document.conclusion(conclusion(listing, result), Tone.GOOD)
        document.section(null, Section.QUERY).fields(query(result))
        AnalyzeLists.render(listing, context, document)
        AnalyzeLists.tests(result, context, document)
        scope(result, document)
        gaps(result, document)
        ConnectednessText.append(result, listing.code, context, document)
        document.section("Context size", Section.CONTEXT).fields(estimates(result))
        val step = if (context.detail == DetailLevel.BRIEF) BRIEF_NEXT_STEP else NEXT_STEP
        return document.section(null, Section.NEXT).note(step).toString()
    }

    private fun nothingFound(result: AnalysisResult, document: TextDocument): String =
        document.conclusion("Nothing in the index matches this task.", Tone.WARNING)
            .line("Terms searched: ${Format.listing(result.query.terms, TERMS_SHOWN)}")
            .hint("Name a class, module or feature of the code, or run `kscope scan` if the code changed.")
            .toString()

    private fun conclusion(listing: Listing, result: AnalysisResult): String {
        val modules = result.scope.modules.size
        val touching = if (modules > 0) ", touching ${Format.noun(modules, "module")}" else ""
        return "${Format.noun(listing.edit.size, "file")} to edit and ${Format.count(listing.read.size)} to read" +
            "$touching; ${Format.noun(listing.scored, "file")} scored; ${listing.strength.wording}."
    }

    private fun query(result: AnalysisResult): List<Pair<String, String>> = buildList {
        val query = result.query
        add("Task" to Format.clip(query.text, TASK_WIDTH))
        add("Terms" to Format.listing(query.terms, TERMS_SHOWN))
        if (query.expansions.isNotEmpty()) {
            add("Also searched" to query.expansions.joinToString(", ") { "${it.term} as ${it.replacement}" })
        }
        if (query.hints.isNotEmpty()) add("Looks like" to query.hints.joinToString(", ") { it.name.lowercase() })
    }

    private fun scope(result: AnalysisResult, document: TextDocument) {
        val scope = result.scope
        val fields = listOf(
            "Modules" to scope.modules.joinToString(", ") { NodeLabels.module(it) },
            "Source sets" to scope.sourceSets.joinToString(", ") { NodeLabels.sourceSet(it) },
            "Platforms" to scope.platforms.joinToString(", "),
            "Variants" to scope.variants.joinToString(", "),
            "Crosses modules" to if (scope.crossModule) "yes" else "no",
        ).filter { it.second.isNotEmpty() }
        document.section("Scope of the change", Section.SCOPE).fields(fields)
    }

    private fun gaps(result: AnalysisResult, document: TextDocument) {
        val paths = Format.noun(result.boundaryPaths.size, "path")
        val truncated = if (result.boundaryPathsTruncated) " (more exist)" else ""
        document.section("Boundaries and gaps", Section.GAPS).fields(
            listOf(
                "Boundary links" to Format.count(result.boundaries.size),
                "Boundary chains" to "$paths$truncated",
                "Unresolved nearby" to Format.count(result.unresolved.size),
            ),
        )
    }

    private fun estimates(result: AnalysisResult): List<Pair<String, String>> =
        result.contextEstimates.map { (level, tokens) ->
            level.name.lowercase().replaceFirstChar { it.uppercase() } to "${Format.count(tokens.value)} tokens"
        }
}
