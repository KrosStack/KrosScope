package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.store.sqlite.ReadOnlyConnection
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException

/** Looks into an index file without creating, migrating or discarding anything. */
public object IndexProbe {
    /** Whether [databaseFile] holds an index of the current schema version with at least one artifact. */
    public fun hasArtifacts(databaseFile: Path): Boolean = inspect(databaseFile) == IndexState.Readable

    public fun inspect(databaseFile: Path): IndexState {
        if (!Files.isRegularFile(databaseFile)) return IndexState.Absent
        return try {
            ReadOnlyConnection.open(databaseFile).use(IndexFileState::of)
        } catch (failure: SQLException) {
            IndexState.Damaged(failure.message.orEmpty())
        }
    }
}
