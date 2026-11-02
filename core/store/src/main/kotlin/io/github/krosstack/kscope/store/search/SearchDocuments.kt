package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.search.CodeTokenizer
import io.github.krosstack.kscope.store.codec.NodeRefs

/** The text of each column of the document that a symbol or an artifact gets in the search index. */
internal object SearchDocuments {
    fun symbol(symbol: Symbol): Map<String, String> = mapOf(
        SymbolColumns.NAME to CodeTokenizer.identifiers(symbol.name).joinToString(" "),
        SymbolColumns.NAME_PARTS to CodeTokenizer.parts(symbol.name).joinToString(" "),
        SymbolColumns.QUALIFIED_PARTS to CodeTokenizer.indexText(symbol.qualifiedName),
        SymbolColumns.SIGNATURE to CodeTokenizer.indexText(symbol.signature),
        SymbolColumns.DOCUMENTATION to CodeTokenizer.indexText(symbol.documentation),
        SymbolColumns.METADATA_PARTS to CodeTokenizer.indexText(
            symbol.attributes.toSortedMap().entries.joinToString(" ") { "${it.key} ${it.value}" },
        ),
        SymbolColumns.PATH_PARTS to CodeTokenizer.indexText(declaringPath(symbol)),
        SymbolColumns.KIND to CodeTokenizer.indexText(symbol.kind.id),
    )

    fun artifact(artifact: Artifact, text: String?): Map<String, String> = mapOf(
        ArtifactColumns.PATH_PARTS to CodeTokenizer.indexText(artifact.ref.path.value),
        ArtifactColumns.CONTENT to CodeTokenizer.indexText(text),
    )

    fun isIndexable(symbol: Symbol): Boolean = symbol.origin !is SymbolOrigin.Library

    fun nodeKey(artifact: Artifact): String = NodeRefs.keyOf(NodeRef.OfArtifact(artifact.ref))

    private fun declaringPath(symbol: Symbol): String? {
        val declared = symbol.declaration?.artifact?.path?.value
        if (declared != null) return declared
        val sourceOfTruth = (symbol.origin as? SymbolOrigin.Generated)?.sourceOfTruth
        return (sourceOfTruth as? NodeRef.OfArtifact)?.ref?.path?.value
    }
}
