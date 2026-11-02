package io.github.krosstack.kscope.spi.search

import io.github.krosstack.kscope.model.Artifact

/** What a store needs from the file system to index the text of artifacts. Implemented by the engine. */
public interface SearchContent {
    /** True when the text of [artifact] belongs in the index; false when only its path does. */
    public fun isIndexed(artifact: Artifact): Boolean

    /** The text of [artifact], or null when it cannot be read. The store tokenizes it and keeps nothing else. */
    public fun textOf(artifact: Artifact): String?
}

/** One document of the search index with the text of its fields, in canonical form for verification. */
public data class IndexedDocument(
    public val scope: SearchScope,
    public val key: String,
    public val fields: Map<String, String>,
)
