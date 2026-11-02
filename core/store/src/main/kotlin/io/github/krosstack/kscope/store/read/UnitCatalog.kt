package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.graph.StoredUnit
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.sqlite.Database

internal class UnitRow(val id: Long, val stored: StoredUnit)

internal class UnitCatalog(private val database: Database) {
    fun units(): List<StoredUnit> = rows().map { it.stored }.sortedWith(canonicalOrder)

    fun rows(): List<UnitRow> = query("")

    fun row(producer: ProducerRef, owner: OwnershipUnit): UnitRow? = query(
        " WHERE p.name = ? AND p.version = ? AND u.kind = ? AND u.key = ?",
        listOf(producer.id, producer.version, Wires.ownershipKind.nameOf(owner.kind), owner.key),
    ).singleOrNull()

    private fun query(where: String, parameters: List<Any> = emptyList()): List<UnitRow> = database.query(
        "SELECT u.id, p.name, p.version, u.kind, u.key, u.fingerprint " +
            "FROM owner_unit u JOIN producer p ON p.id = u.producer$where",
        parameters,
    ) { cursor ->
        UnitRow(
            id = cursor.long(),
            stored = StoredUnit(
                producer = ProducerRef(cursor.text(), cursor.text()),
                owner = OwnershipUnit(Wires.ownershipKind.valueOf(cursor.text()), cursor.text()),
                fingerprint = Fingerprint(cursor.text()),
            ),
        )
    }

    fun recreate(batch: FactBatch): Long {
        val producer = producerId(batch.producer)
        val kind = Wires.ownershipKind.nameOf(batch.owner.kind)
        database.update(
            "DELETE FROM owner_unit WHERE producer = ? AND kind = ? AND key = ?",
            listOf(producer, kind, batch.owner.key),
        )
        return database.insertReturningKey(
            "INSERT INTO owner_unit (producer, kind, key, fingerprint) VALUES (?, ?, ?, ?)",
            listOf(producer, kind, batch.owner.key, batch.inputFingerprint.value),
        )
    }

    fun remove(producer: ProducerRef, owner: OwnershipUnit) {
        database.update(
            "DELETE FROM owner_unit WHERE kind = ? AND key = ? AND producer IN " +
                "(SELECT id FROM producer WHERE name = ? AND version = ?)",
            listOf(Wires.ownershipKind.nameOf(owner.kind), owner.key, producer.id, producer.version),
        )
        database.update("DELETE FROM producer WHERE id NOT IN (SELECT producer FROM owner_unit)")
    }

    private fun producerId(producer: ProducerRef): Long {
        val parameters = listOf(producer.id, producer.version)
        database.update("INSERT OR IGNORE INTO producer (name, version) VALUES (?, ?)", parameters)
        val ids = database.query("SELECT id FROM producer WHERE name = ? AND version = ?", parameters) { it.long() }
        return ids.single()
    }

    private companion object {
        val canonicalOrder = compareBy<StoredUnit>(
            { it.producer.id },
            { it.producer.version },
            { it.owner.kind.name },
            { it.owner.key },
        )
    }
}
