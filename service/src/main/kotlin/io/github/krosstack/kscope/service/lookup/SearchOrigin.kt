package io.github.krosstack.kscope.service.lookup

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

public data class SearchOrigin(
    public val narrowing: RepositoryNarrowing? = null,
    public val outside: RepositoryNarrowing? = null,
    public val widenedFrom: RepositoryNarrowing? = null,
    public val incomplete: Boolean = false,
) {
    @PublishedApi
    internal fun fields(): List<Pair<String, JsonElement>> = listOfNotNull(
        narrowing?.let { NARROWED_TO to JsonPrimitive(it.name) },
        outside?.let { OUTSIDE_NARROWING to JsonPrimitive(it.name) },
        widenedFrom?.let { WIDENED_FROM to JsonPrimitive(it.name) },
        if (incomplete) INCOMPLETE to JsonPrimitive(true) else null,
    )

    public companion object {
        public const val NARROWED_TO: String = "narrowedTo"
        public const val OUTSIDE_NARROWING: String = "outsideNarrowing"
        public const val WIDENED_FROM: String = "widenedFrom"
        public const val INCOMPLETE: String = "incomplete"

        public fun limited(narrowing: RepositoryNarrowing?, incomplete: Boolean): SearchOrigin =
            SearchOrigin(narrowing, incomplete = narrowing != null && incomplete)
    }
}
