package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.model.Module

/**
 * A database of the module as the build script declares it: `sqldelight.database.<name>.package` in the model,
 * `sqldelight.database.<name>.srcDirs` for the source directories it configures, relative to the module, and
 * `sqldelight.database.<name>.srcDirsUnread` when it configures directories the build reading could not resolve.
 */
internal data class SqlDelightDatabase(
    val name: String,
    val packageName: String,
    val sourceDirectories: List<String> = emptyList(),
    val directoriesUnread: Boolean = false,
) {
    val configuresDirectories: Boolean get() = directoriesUnread || sourceDirectories.isNotEmpty()
}

internal object SqlDelightDatabases {
    private val packageAttribute = Regex("""^sqldelight\.database\.(.+)\.package$""")

    fun of(module: Module): List<SqlDelightDatabase> = module.attributes.mapNotNull { (key, value) ->
        packageAttribute.find(key)?.let { match ->
            val name = match.groupValues[1]
            val directories = module.attributes["sqldelight.database.$name.srcDirs"].orEmpty()
            val unread = module.attributes["sqldelight.database.$name.srcDirsUnread"] == "true"
            SqlDelightDatabase(name, value, directories.split(',').filter { it.isNotEmpty() }, unread)
        }
    }.sortedBy { it.name }

    /**
     * The only database; or among several the one whose configured source directories contain the file, or when none
     * does and every configured directory was read, the only one without configured directories whose lower-case name
     * is a directory of the file. Null when no database or several of them can claim the file.
     */
    fun forFile(databases: List<SqlDelightDatabase>, modulePath: String, path: SqPath): SqlDelightDatabase? {
        if (databases.size <= 1) return databases.singleOrNull()
        val configured = databases.filter { it.configuresDirectories }
        val containing = configured.filter { database ->
            database.sourceDirectories.any { modulePath == it || modulePath.startsWith("$it/") }
        }
        return when {
            containing.isNotEmpty() -> containing.singleOrNull()
            configured.any { it.directoriesUnread } -> null
            else -> namedBy(databases - configured.toSet(), path)
        }
    }

    private fun namedBy(databases: List<SqlDelightDatabase>, path: SqPath): SqlDelightDatabase? {
        val directories = path.directories.map { it.lowercase() }
        return databases.singleOrNull { it.name.lowercase() in directories }
    }
}
