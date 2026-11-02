package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.model.Module

/** A database of the module as the build script declares it: `sqldelight.database.<name>.package` in the model. */
internal data class SqlDelightDatabase(val name: String, val packageName: String)

internal object SqlDelightDatabases {
    private val attribute = Regex("""^sqldelight\.database\.(.+)\.package$""")

    fun of(module: Module): List<SqlDelightDatabase> = module.attributes.mapNotNull { (key, value) ->
        attribute.find(key)?.let { SqlDelightDatabase(it.groupValues[1], value) }
    }.sortedBy { it.name }

    /** The only database, or among several the one whose lower-case name is a directory of the file, or the first. */
    fun forFile(databases: List<SqlDelightDatabase>, path: SqPath): SqlDelightDatabase? {
        val directories = path.directories.map { it.lowercase() }
        return databases.singleOrNull()
            ?: databases.firstOrNull { it.name.lowercase() in directories }
            ?: databases.firstOrNull()
    }
}
