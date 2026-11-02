package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind

internal object SqlDelightKinds {
    val table = SymbolKind("sqldelight.table", SymbolCategory.SCHEMA)
    val column = SymbolKind("sqldelight.column", SymbolCategory.SCHEMA)
    val query = SymbolKind("sqldelight.query", SymbolCategory.SCHEMA)
}

/** Where a `.sq` file lies below the `sqldelight` directory of its source set; that gives its package. */
internal class SqPath(path: String) {
    private val segments = path.split('/')
    private val rootIndex = segments.indexOfLast { it == ROOT }

    val isMigration: Boolean = segments.last().endsWith(".sqm")

    /** The directories between the `sqldelight` directory and the file. */
    val directories: List<String> =
        if (rootIndex < 0) emptyList() else segments.subList(rootIndex + 1, segments.size - 1)

    /** The name of the file without its extension; the `.sq` file `Order.sq` is `Order`. */
    val baseName: String = segments.last().substringBeforeLast('.')

    /** The package of the generated classes: the directories as a package, or the package of the database. */
    fun packageName(database: SqlDelightDatabase?): String =
        directories.joinToString(".").ifEmpty { database?.packageName.orEmpty() }

    private companion object {
        const val ROOT = "sqldelight"
    }
}

/** The names that SQLDelight gives the classes and properties it generates. */
internal object SqlDelightNaming {
    fun tableClass(tableName: String): String = tableName.replaceFirstChar { it.uppercase() }

    fun queriesClass(baseName: String): String = baseName.replaceFirstChar { it.uppercase() } + "Queries"

    fun queriesProperty(baseName: String): String = baseName.replaceFirstChar { it.lowercase() } + "Queries"

    fun projectionClass(label: String): String = label.replaceFirstChar { it.uppercase() }

    fun qualified(packageName: String, name: String): String = if (packageName.isEmpty()) name else "$packageName.$name"
}
