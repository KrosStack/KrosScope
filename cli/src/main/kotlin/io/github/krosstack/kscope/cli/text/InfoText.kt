package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.service.CoverageEntry
import io.github.krosstack.kscope.service.InfoResult
import io.github.krosstack.kscope.service.RepositoryEntry

/** The result of `info`: what the index holds. */
internal object InfoText {
    fun render(result: InfoResult, context: TextContext): String {
        val document = TextDocument(context)
        val location = context.relative(result.indexLocation)
        document.conclusion(conclusion(result), Tone.GOOD).line("kscope ${result.toolVersion}, index in $location")
        structure(result, document)
        repositories(result.repositories, document)
        counts("Files by type", result.artifactsByType, document)
        counts("Symbols by kind", result.symbolsByKind, document)
        relationships(result.coverage, document)
        if (result.metricsByName.isNotEmpty()) counts("Metrics", result.metricsByName, document)
        document.section("Index health").fields(health(result))
        document.section("Settings").fields(SettingsText.fields(result.settings))
        return document.toString()
    }

    private fun conclusion(result: InfoResult): String {
        val files = Format.noun(result.artifactsByType.values.sum(), "file")
        val modules = Format.noun(result.structure.modules.toLong(), "module")
        val symbols = Format.noun(result.symbolsByKind.values.sum(), "symbol")
        val relationships = Format.noun(result.coverage.sumOf { it.count }, "relationship")
        return "Indexed $files in $modules: $symbols and $relationships."
    }

    private fun structure(result: InfoResult, document: TextDocument) {
        val structure = result.structure
        document.section("Structure").fields(
            listOf(
                "Repositories" to Format.count(structure.repositories),
                "Build roots" to Format.count(structure.buildRoots),
                "Modules" to Format.count(structure.modules),
                "Source sets" to Format.count(structure.sourceSets),
                "Targets" to Format.count(structure.targets),
                "Variants" to Format.count(structure.variants),
            ),
        )
    }

    private fun repositories(repositories: List<RepositoryEntry>, document: TextDocument) {
        if (repositories.isEmpty()) return
        val table = Table(listOf(Column("directory", fit = Fit.PATH), Column("id"), Column("modules", Align.RIGHT)))
        repositories.forEach { table.row(it.directory, it.id.take(SHORT_ID), Format.count(it.modules)) }
        document.section("Repositories (${Format.count(repositories.size)})").table(table)
    }

    private fun counts(title: String, byKey: Map<String, Long>, document: TextDocument) {
        if (byKey.isEmpty()) return
        val ordered = byKey.entries.sortedWith(
            compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key },
        )
        val table = Table(listOf(Column(), Column(align = Align.RIGHT)))
        ordered.take(TextDocument.DEFAULT_LIMIT).forEach { table.row(it.key, Format.count(it.value)) }
        document.section("$title (${Format.count(byKey.values.sum())})").table(table)
            .more(ordered.size - TextDocument.DEFAULT_LIMIT)
    }

    private fun relationships(coverage: List<CoverageEntry>, document: TextDocument) {
        if (coverage.isEmpty()) return
        val ordered = coverage.sortedWith(compareByDescending<CoverageEntry> { it.count }.thenBy { it.relation })
        val table = Table(listOf(Column("relation"), Column("evidence"), Column("state"), Column("count", Align.RIGHT)))
        ordered.take(TextDocument.DEFAULT_LIMIT).forEach {
            table.row(it.relation, it.evidence, it.state, Format.count(it.count))
        }
        document.section("Relationships (${Format.count(coverage.sumOf { it.count })})").table(table)
            .more(ordered.size - TextDocument.DEFAULT_LIMIT)
    }

    private fun health(result: InfoResult): List<Pair<String, String>> = listOf(
        "Level" to (result.indexLevel ?: "none"),
        "Diagnostics" to Format.count(result.diagnostics),
        "Search index" to if (result.searchIndexPopulated) "populated" else "empty",
    )

    private const val SHORT_ID = 8
}
