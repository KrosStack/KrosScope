package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.DifferenceKind
import io.github.krosstack.kscope.engine.UnitDifference
import io.github.krosstack.kscope.engine.VerifyReport
import io.github.krosstack.kscope.engine.pipeline.UnitKey
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.search.IndexedDocument

/** What a store holds: the facts of its units and the documents of its search index. */
internal class StoreContent(val batches: List<FactBatch>, val searchDocuments: List<IndexedDocument>)

/** Compares the canonical content of an incrementally updated store with that of a store built from scratch. */
internal object VerifyComparator {
    private const val LISTED_DIFFERENCES = 10

    private val keyOrder = compareBy<UnitKey>(
        { it.producer.id },
        { it.producer.version },
        { it.owner.kind.name },
        { it.owner.key },
    )

    fun compare(incremental: StoreContent, rebuilt: StoreContent): VerifyReport {
        val incrementalByKey = incremental.batches.associateBy { UnitKey(it.producer, it.owner) }
        val rebuiltByKey = rebuilt.batches.associateBy { UnitKey(it.producer, it.owner) }
        val differences = (incrementalByKey.keys + rebuiltByKey.keys).sortedWith(keyOrder).mapNotNull { key ->
            differenceOf(key, incrementalByKey[key], rebuiltByKey[key])
        }
        val documents = differingDocuments(incremental.searchDocuments, rebuilt.searchDocuments)
        return VerifyReport(
            matches = differences.isEmpty() && documents == 0,
            differingUnits = differences.size,
            differences = differences.take(LISTED_DIFFERENCES),
            differingSearchDocuments = documents,
        )
    }

    private fun differingDocuments(incremental: List<IndexedDocument>, rebuilt: List<IndexedDocument>): Int {
        val incrementalByKey = incremental.associateBy { it.scope to it.key }
        val rebuiltByKey = rebuilt.associateBy { it.scope to it.key }
        return (incrementalByKey.keys + rebuiltByKey.keys).count { incrementalByKey[it] != rebuiltByKey[it] }
    }

    private fun differenceOf(key: UnitKey, incremental: FactBatch?, rebuilt: FactBatch?): UnitDifference? {
        val kind = when {
            incremental == null -> DifferenceKind.MISSING_FROM_INCREMENTAL
            rebuilt == null -> DifferenceKind.UNEXPECTED_IN_INCREMENTAL
            incremental != rebuilt -> DifferenceKind.DIFFERENT_CONTENT
            else -> return null
        }
        return UnitDifference(key.producer, key.owner, kind)
    }
}
