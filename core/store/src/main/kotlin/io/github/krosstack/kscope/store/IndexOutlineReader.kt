package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.read.CanonicalOrder
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.ReadOnlyConnection
import io.github.krosstack.kscope.store.structure.RepositoryTable
import io.github.krosstack.kscope.store.table.select
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException

public object IndexOutlineReader {
    public fun read(databaseFile: Path): IndexRead {
        if (!Files.isRegularFile(databaseFile)) return IndexRead.Unusable(IndexState.Absent)
        return try {
            ReadOnlyConnection.open(databaseFile).use(::outlineOf)
        } catch (failure: SQLException) {
            IndexRead.Unusable(IndexState.Damaged(failure.message.orEmpty()))
        }
    }

    private fun outlineOf(connection: Connection): IndexRead {
        val state = IndexFileState.of(connection)
        if (state != IndexState.Readable) return IndexRead.Unusable(state)
        val database = Database(connection)
        val repositories = CanonicalOrder.sorted(database.select(RepositoryTable), RepositoryTable::wrap)
            .distinctBy { it.id }
        val units = database.query(
            "SELECT DISTINCT p.name, p.version, u.kind FROM owner_unit u JOIN producer p ON p.id = u.producer",
        ) { UnitKind(ProducerRef(it.text(), it.text()), Wires.ownershipKind.valueOf(it.text())) }
        return IndexRead.Found(IndexOutline(repositories, units.toSet()))
    }
}
