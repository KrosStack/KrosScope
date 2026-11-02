package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.table.FactTable
import io.github.krosstack.kscope.store.table.FactTables

internal class BatchReader(private val database: Database, private val catalog: UnitCatalog) {
    fun batches(): List<FactBatch> {
        val factsByUnit = FactTables.all.flatMap { factsOf(it) }.groupBy({ it.first }, { it.second })
        val batches = catalog.rows().map { row ->
            FactBatch(row.stored.producer, row.stored.owner, row.stored.fingerprint, factsByUnit[row.id].orEmpty())
        }
        return CanonicalFacts.sorted(batches)
    }

    fun batch(producer: ProducerRef, owner: OwnershipUnit): FactBatch? {
        val row = catalog.row(producer, owner) ?: return null
        val facts = FactTables.all.flatMap { factsOf(it, row.id) }
        return CanonicalFacts.sorted(FactBatch(producer, owner, row.stored.fingerprint, facts))
    }

    private fun <T : Any> factsOf(table: FactTable<T>, unit: Long): List<Fact> = database.query(
        "SELECT ${table.spec.selectList} FROM ${table.spec.name} WHERE unit = ?",
        listOf(unit),
    ) { cursor -> table.wrap(table.read(cursor)) }

    private fun <T : Any> factsOf(table: FactTable<T>): List<Pair<Long, Fact>> = database.query(
        "SELECT unit, ${table.spec.selectList} FROM ${table.spec.name}",
    ) { cursor -> cursor.long() to table.wrap(table.read(cursor)) }
}
