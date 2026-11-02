package io.github.krosstack.kscope.store.sqlite

import io.github.krosstack.kscope.store.search.SearchLayouts
import io.github.krosstack.kscope.store.search.SymbolIndexer
import io.github.krosstack.kscope.store.table.FactTables

internal object Schema {
    const val VERSION = 6

    private val coreStatements = listOf(
        "CREATE TABLE producer (" +
            "id INTEGER PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL, UNIQUE (name, version))",
        "CREATE TABLE owner_unit (" +
            "id INTEGER PRIMARY KEY, " +
            "producer INTEGER NOT NULL REFERENCES producer(id) ON DELETE CASCADE, " +
            "kind TEXT NOT NULL, key TEXT NOT NULL, fingerprint TEXT NOT NULL, " +
            "UNIQUE (producer, kind, key))",
    )

    private val searchStatements = SearchLayouts.all.flatMap { it.createStatements } +
        "CREATE TABLE ${SymbolIndexer.DIRTY} (node_key TEXT NOT NULL PRIMARY KEY) WITHOUT ROWID"

    val creationStatements: List<String> =
        coreStatements + FactTables.all.flatMap { it.spec.createStatements } + searchStatements
}
