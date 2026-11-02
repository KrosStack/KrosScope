package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.store.sqlite.Database

internal class FactWriter(private val database: Database) {
    fun requireUniqueSymbols(batch: FactBatch) {
        val seen = HashSet<String>()
        batch.facts.asSequence().mapNotNull(SymbolTable::unwrap).forEach { symbol ->
            require(seen.add(symbol.id.value)) {
                "Duplicate symbol id in batch of ${batch.producer.id} for ${batch.owner.key}: ${symbol.id.value}"
            }
        }
    }

    fun write(unit: Long, facts: List<Fact>) {
        FactTables.all.forEach { writeRows(it, unit, facts) }
    }

    private fun <T : Any> writeRows(table: FactTable<T>, unit: Long, facts: List<Fact>) {
        val values = facts.mapNotNull(table::unwrap)
        if (values.isEmpty()) return
        database.insertAll(table.spec.insertSql, values) { binder, value ->
            binder.long(unit)
            table.bind(binder, value)
        }
    }
}
