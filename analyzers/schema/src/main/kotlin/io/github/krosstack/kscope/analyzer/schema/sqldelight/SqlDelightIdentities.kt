package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.namespaces
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId

/**
 * The ids of the symbols of a `.sq` file in the module and source set of the file. Tables and their columns are under
 * `sqldelight/tables/<package>/<Table>#`, queries under `sqldelight/queries/<package>/<File>#<label>.`; the generated
 * classes use the same ids for the sources of truth they point at.
 */
internal class SqlDelightIdentities(private val identity: IdentityFactory, private val packageName: String) {
    fun tableId(tableName: String): SymbolId = identity.idOf(tableDescriptor(tableName))

    fun columnId(tableName: String, columnName: String): SymbolId =
        identity.idOf(tableDescriptor(tableName).term(columnName))

    fun queryId(fileBaseName: String, label: String): SymbolId = identity.idOf(
        SymbolDescriptor().namespace("sqldelight").namespace("queries").namespaces(packageName)
            .type(fileBaseName).term(label),
    )

    fun tableQualifiedName(tableName: String): String = SqlDelightNaming.qualified(packageName, tableName)

    private fun tableDescriptor(tableName: String): SymbolDescriptor =
        SymbolDescriptor().namespace("sqldelight").namespace("tables").namespaces(packageName).type(tableName)
}
