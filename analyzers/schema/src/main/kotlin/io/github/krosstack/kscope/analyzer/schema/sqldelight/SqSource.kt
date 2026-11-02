package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqFile
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqFileReader
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTableReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

/** A `.sq` file read and split, with the database it belongs to, the tables it defines and the ids of its symbols. */
internal class SqSource(val artifact: Artifact, val placement: Placement, val text: String, val file: SqFile) {
    val path = SqPath(artifact.ref.path.value)
    val database: SqlDelightDatabase? = SqlDelightDatabases.forFile(SqlDelightDatabases.of(placement.module), path)
    val packageName: String = path.packageName(database)
    val ids = SqlDelightIdentities(placement.identity, packageName)
    val tables: List<SqlTable> = file.tables.mapNotNull { SqlTableReader.read(it, file.imports) }

    companion object {
        /** Null for a file that is not a `.sq` file, that no module contains or that cannot be read. */
        fun read(artifact: Artifact, model: BuildModel, files: WorkspaceFiles): SqSource? {
            val placement = Placement.of(artifact, model)
            val text = try {
                files.read(artifact.ref).toString(Charsets.UTF_8)
            } catch (_: IOException) {
                null
            }
            val isMigration = artifact.ref.path.value.endsWith(".sqm")
            if (isMigration || placement == null || text == null) return null
            return SqSource(artifact, placement, text, SqFileReader.read(text))
        }
    }
}
