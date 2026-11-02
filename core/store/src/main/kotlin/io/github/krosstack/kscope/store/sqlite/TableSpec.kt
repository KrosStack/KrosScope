package io.github.krosstack.kscope.store.sqlite

internal class IndexSpec(val columns: List<String>, val unique: Boolean = false)

internal fun indexOn(vararg columns: String) = IndexSpec(columns.toList())

/**
 * A fact table. Every row carries the owning unit in its first column; [columns] are `name TYPE [constraints]`
 * definitions in the order that the table's binder and reader use.
 */
internal class TableSpec(val name: String, columns: List<String>, indexes: List<IndexSpec> = emptyList()) {
    private val columnNames = columns.map { it.substringBefore(' ') }
    private val allIndexes =
        if (indexes.any { it.columns.first() == UNIT }) indexes else indexes + indexOn(UNIT)

    val selectList: String = columnNames.joinToString(", ")

    val insertSql: String = "INSERT INTO $name ($UNIT, $selectList) VALUES (${placeholders(columnNames.size + 1)})"

    val createStatements: List<String> =
        listOf(createTable(columns)) + allIndexes.map(::createIndex)

    private fun createTable(columns: List<String>): String {
        val definitions = listOf("$UNIT INTEGER NOT NULL REFERENCES owner_unit(id) ON DELETE CASCADE") + columns
        return "CREATE TABLE $name (${definitions.joinToString(", ")})"
    }

    private fun createIndex(index: IndexSpec): String {
        val unique = if (index.unique) "UNIQUE " else ""
        val indexName = "idx_${name}_${index.columns.joinToString("_")}"
        return "CREATE ${unique}INDEX $indexName ON $name (${index.columns.joinToString(", ")})"
    }

    private fun placeholders(count: Int) = List(count) { "?" }.joinToString(", ")

    private companion object {
        const val UNIT = "unit"
    }
}
