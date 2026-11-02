package io.github.krosstack.kscope.spi.graph

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.search.IndexedDocument
import io.github.krosstack.kscope.spi.search.SearchContent
import io.github.krosstack.kscope.spi.search.SearchIndex

public data class StoredUnit(
    public val producer: ProducerRef,
    public val owner: OwnershipUnit,
    public val fingerprint: Fingerprint,
)

public interface GraphStore :
    GraphReader,
    AutoCloseable {
    public fun units(): List<StoredUnit>

    /** Atomically replaces everything the batch's producer and owner stored before. */
    public fun replace(batch: FactBatch)

    public fun remove(producer: ProducerRef, owner: OwnershipUnit)

    /** Runs [block] atomically: when it throws, every write made inside is rolled back. */
    public fun <T> transaction(block: () -> T): T

    /** The full canonical content, for dumps and verify mode. */
    public fun batches(): List<FactBatch>

    /** The stored content of one ownership unit in canonical order, or null when the unit is not stored. */
    public fun batch(producer: ProducerRef, owner: OwnershipUnit): FactBatch?

    public fun searchIndex(): SearchIndex

    /**
     * Brings the search index up to date with what [replace] and [remove] changed since the last refresh, and with the
     * artifacts that were added, changed or deleted. Documents that did not change are not touched.
     */
    public fun refreshSearchIndex(content: SearchContent)

    /** The documents of the search index in canonical order, for dumps and verify mode. */
    public fun searchDocuments(): List<IndexedDocument>
}
