package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.MatchStrength
import io.github.krosstack.kscope.cli.text.format.RelevanceCutoff
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.service.ArtifactKind

/** The relevant files of a task analysis, split by what they are. */
internal data class Listing(
    val code: List<RankedFile>,
    val documents: List<RankedFile>,
    val binaries: Int,
    val scored: Int,
    val belowCutoff: Int,
    val strength: MatchStrength,
) {
    val edit: List<RankedFile> = code.filter { it.role == FileRole.EDIT }
    val read: List<RankedFile> = code.filter { it.role == FileRole.READ }

    companion object {
        fun of(result: AnalysisResult, kinds: Map<ArtifactRef, ArtifactKind>): Listing {
            val selection = RelevanceCutoff.select(result.files) { it.score }
            val byKind = selection.relevant.groupBy { kinds[it.artifact] ?: ArtifactKind.CODE }
            return Listing(
                code = byKind[ArtifactKind.CODE].orEmpty(),
                documents = byKind[ArtifactKind.DOCUMENT].orEmpty(),
                binaries = byKind[ArtifactKind.BINARY].orEmpty().size,
                scored = selection.scored,
                belowCutoff = selection.belowCutoff,
                strength = MatchStrength.of(result.files.map { it.score }),
            )
        }
    }
}

/** The lists of a task analysis: the best candidates, files to edit and read, documents, the cut-off and tests. */
internal object AnalyzeLists {
    private const val ROWS = 10
    private const val SIGNALS_SHOWN = 2
    private const val MORE_RELEVANT = "more relevant (use --detail full to list them)"

    fun render(listing: Listing, context: TextContext, document: TextDocument) {
        top(listing, context, document)
        files("Files to edit", Section.EDIT, listing.edit, context, document)
        files("Files to read", Section.READ, listing.read, context, document)
        documents(listing, context, document)
        cutoff(listing, document)
    }

    fun tests(result: AnalysisResult, context: TextContext, document: TextDocument) {
        if (result.tests.isEmpty()) return
        val paths = RelevanceCutoff.select(result.tests) { it.score }.relevant.map { context.path(it.artifact) }
        if (context.detail == DetailLevel.BRIEF && context.sections == null) {
            document.section(null, Section.TESTS).line("  Tests to run: ${Format.count(paths.size)}")
            return
        }
        val shown = paths.take(context.detail.rows(ROWS))
        document.section(heading("Tests to run", shown.size, paths.size), Section.TESTS)
            .items(shown, shown.size).more(paths.size - shown.size, MORE_RELEVANT)
    }

    private fun top(listing: Listing, context: TextContext, document: TextDocument) {
        document.section("Best candidates", Section.TOP)
        val shown = listing.code.take(context.detail.rows(DetailLevel.BRIEF_ROWS))
        val table = Table(listOf(Column("score", Align.RIGHT), Column("role"), Column("file", fit = Fit.PATH)))
        shown.forEach {
            table.row(Format.score(it.score), it.role.name.lowercase(), context.path(it.artifact))
        }
        document.table(table).more(listing.code.size - shown.size, MORE_RELEVANT)
    }

    private fun files(title: String, name: String, files: List<RankedFile>, context: TextContext, d: TextDocument) {
        if (files.isEmpty()) return
        val shown = files.take(context.detail.rows(ROWS))
        d.section(heading(title, shown.size, files.size), name).table(because(shown, context))
            .more(files.size - shown.size, MORE_RELEVANT)
    }

    private fun documents(listing: Listing, context: TextContext, document: TextDocument) {
        if (listing.documents.isEmpty() && listing.binaries == 0) return
        val shown = listing.documents.take(context.detail.rows(ROWS))
        val title = if (shown.isEmpty()) null else heading("Documents", shown.size, listing.documents.size)
        document.section(title, Section.DOCUMENTS)
        if (shown.isNotEmpty()) {
            document.table(because(shown, context)).more(listing.documents.size - shown.size, MORE_RELEVANT)
        }
        if (listing.binaries > 0) {
            val noun = Format.noun(listing.binaries, "image or other binary file", "images and other binary files")
            document.note("  Not listed: $noun.")
        }
    }

    private fun cutoff(listing: Listing, document: TextDocument) {
        if (listing.belowCutoff == 0) return
        document.section(null, Section.CUTOFF).note(
            "  ${Format.noun(listing.belowCutoff, "more file")} scored below ${RelevanceCutoff.percent()} of the " +
                "best score (--format json lists all).",
        )
    }

    private fun because(files: List<RankedFile>, context: TextContext): Table {
        val table = Table(listOf(Column("score", Align.RIGHT), Column("file", fit = Fit.PATH), Column("because")))
        files.forEach { table.row(Format.score(it.score), context.path(it.artifact), reasons(it)) }
        return table
    }

    private fun heading(title: String, shown: Int, relevant: Int): String =
        if (shown == relevant) "$title (${Format.count(relevant)})" else "$title (showing $shown of $relevant)"

    private fun reasons(file: RankedFile): String =
        file.reasons.sortedByDescending { it.score }.take(SIGNALS_SHOWN).joinToString(", ") {
            it.signal.name.lowercase().replace('_', ' ')
        }
}
