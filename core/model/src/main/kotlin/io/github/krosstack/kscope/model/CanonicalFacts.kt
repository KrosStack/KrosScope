package io.github.krosstack.kscope.model

/**
 * Facts are ordered by their canonical JSON: the type discriminator first, then the fields in declaration order.
 * Batches are ordered by producer, owner and input fingerprint.
 */
public object CanonicalFacts {
    private val batchOrder = compareBy<FactBatch>(
        { it.producer.id },
        { it.producer.version },
        { it.owner.kind.name },
        { it.owner.key },
        { it.inputFingerprint.value },
    )

    public fun sorted(batch: FactBatch): FactBatch =
        batch.copy(facts = batch.facts.map(::EncodedFact).sortedBy { it.json }.map { it.fact })

    public fun sorted(batches: List<FactBatch>): List<FactBatch> = batches.map(::sorted).sortedWith(batchOrder)

    public fun toJson(batches: List<FactBatch>): String = KscopeJson.encodeToString<List<FactBatch>>(sorted(batches))
}

private class EncodedFact(val fact: Fact) {
    val json: String = KscopeJson.encodeToString<Fact>(fact)
}
