package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson

internal object CanonicalOrder {
    private val factSerializer = Fact.serializer()

    /** Orders values the way `CanonicalFacts` orders facts of one type: by their canonical JSON. */
    fun <T : Any> sorted(values: List<T>, asFact: (T) -> Fact): List<T> {
        val keyed = values.map { it to key(asFact(it)) }
        return keyed.sortedBy { it.second }.map { it.first }
    }

    /** The canonical JSON of [fact], which is what facts are ordered by. */
    fun key(fact: Fact): String = KscopeJson.encodeToString(factSerializer, fact)
}
