package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.spi.search.SearchScope

internal class SearchField(val column: String, val weight: Double)

/** The tables of one document set of the search index and the BM25 weight of each of its columns. */
internal class SearchLayout(
    val scope: SearchScope,
    val fts: String,
    val fields: List<SearchField>,
    stamped: Boolean = false,
) {
    val documents = "${fts}_doc"
    val vocabulary = "${fts}_vocab"
    val columns: List<String> = fields.map { it.column }

    private val stampDefinitions =
        if (stamped) listOf("content_hash TEXT NOT NULL", "has_content INTEGER NOT NULL") else emptyList()

    /** The columns of the table of keys that say what the document was built from. */
    val stampColumns: List<String> = stampDefinitions.map { it.substringBefore(' ') }

    val createStatements: List<String> = listOf(
        "CREATE TABLE $documents (" +
            (listOf("doc INTEGER PRIMARY KEY", "node_key TEXT NOT NULL UNIQUE") + stampDefinitions)
                .joinToString(", ") + ")",
        "CREATE VIRTUAL TABLE $fts USING fts5(${columns.joinToString(", ")}, tokenize = 'porter unicode61')",
        "CREATE VIRTUAL TABLE $vocabulary USING fts5vocab($fts, 'row')",
    )

    /** The `bm25` call that weighs every column. */
    val scoreSql: String = bm25(fields.map { it.weight })

    /** One `bm25` call per column that weighs that column alone, in column order. */
    val columnScoreSql: List<String> = fields.indices.map { only ->
        bm25(fields.mapIndexed { index, field -> if (index == only) field.weight else 0.0 })
    }

    private fun bm25(weights: List<Double>) = "-bm25($fts, ${weights.joinToString(", ")})"
}

private object SearchWeights {
    const val SYMBOL_NAME = 10.0
    const val SYMBOL_NAME_PARTS = 6.0
    const val SYMBOL_QUALIFIED_PARTS = 3.0
    const val SYMBOL_SIGNATURE = 2.0
    const val SYMBOL_DOCUMENTATION = 1.5
    const val SYMBOL_METADATA_PARTS = 1.0
    const val SYMBOL_PATH_PARTS = 2.0
    const val SYMBOL_KIND = 0.5
    const val ARTIFACT_PATH_PARTS = 4.0
    const val ARTIFACT_CONTENT = 1.0
}

internal object SearchLayouts {
    val symbols = SearchLayout(
        scope = SearchScope.SYMBOLS,
        fts = "search_symbol",
        fields = listOf(
            SearchField(SymbolColumns.NAME, SearchWeights.SYMBOL_NAME),
            SearchField(SymbolColumns.NAME_PARTS, SearchWeights.SYMBOL_NAME_PARTS),
            SearchField(SymbolColumns.QUALIFIED_PARTS, SearchWeights.SYMBOL_QUALIFIED_PARTS),
            SearchField(SymbolColumns.SIGNATURE, SearchWeights.SYMBOL_SIGNATURE),
            SearchField(SymbolColumns.DOCUMENTATION, SearchWeights.SYMBOL_DOCUMENTATION),
            SearchField(SymbolColumns.METADATA_PARTS, SearchWeights.SYMBOL_METADATA_PARTS),
            SearchField(SymbolColumns.PATH_PARTS, SearchWeights.SYMBOL_PATH_PARTS),
            SearchField(SymbolColumns.KIND, SearchWeights.SYMBOL_KIND),
        ),
    )

    val artifacts = SearchLayout(
        scope = SearchScope.ARTIFACTS,
        fts = "search_artifact",
        fields = listOf(
            SearchField(ArtifactColumns.PATH_PARTS, SearchWeights.ARTIFACT_PATH_PARTS),
            SearchField(ArtifactColumns.CONTENT, SearchWeights.ARTIFACT_CONTENT),
        ),
        stamped = true,
    )

    val all = listOf(symbols, artifacts)

    fun of(scope: SearchScope): SearchLayout = when (scope) {
        SearchScope.SYMBOLS -> symbols
        SearchScope.ARTIFACTS -> artifacts
    }
}

internal object SymbolColumns {
    const val NAME = "name"
    const val NAME_PARTS = "name_parts"
    const val QUALIFIED_PARTS = "qualified_parts"
    const val SIGNATURE = "signature"
    const val DOCUMENTATION = "documentation"
    const val METADATA_PARTS = "metadata_parts"
    const val PATH_PARTS = "path_parts"
    const val KIND = "kind"
}

internal object ArtifactColumns {
    const val PATH_PARTS = "path_parts"
    const val CONTENT = "content"
}
