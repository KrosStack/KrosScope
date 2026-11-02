package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.search.SearchContent
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.structure.StructureReader

/** Brings the artifact documents in line with the artifacts: new and changed ones are indexed, deleted ones dropped. */
internal class ArtifactIndexer(private val database: Database, private val structure: StructureReader) {
    private val layout = SearchLayouts.artifacts
    private val table = DocumentTable(database, layout)

    private class Indexed(val doc: Long, val stamp: ContentStamp)

    fun refresh(content: SearchContent) {
        val artifacts = structure.artifacts()
        val indexed = indexedDocuments()
        val present = artifacts.map(SearchDocuments::nodeKey).toSet()
        val stale = artifacts.filter { isStale(it, indexed[SearchDocuments.nodeKey(it)], content) }
        val gone = indexed.filterKeys { it !in present }.values.map { it.doc }
        val replaced = stale.mapNotNull { indexed[SearchDocuments.nodeKey(it)]?.doc }
        (gone + replaced).chunked(DELETE_CHUNK).forEach(table::deleteByDocs)
        stale.sortedBy(SearchDocuments::nodeKey).chunked(INSERT_CHUNK).forEach { chunk ->
            table.insert(chunk.map { document(it, content) })
        }
    }

    private fun isStale(artifact: Artifact, indexed: Indexed?, content: SearchContent): Boolean {
        if (indexed == null) return true
        return indexed.stamp.hash != artifact.contentHash.value ||
            indexed.stamp.hasContent != content.isIndexed(artifact)
    }

    private fun document(artifact: Artifact, content: SearchContent): TextDocument {
        val text = if (content.isIndexed(artifact)) content.textOf(artifact) else null
        return TextDocument(
            key = SearchDocuments.nodeKey(artifact),
            fields = SearchDocuments.artifact(artifact, text),
            stamp = ContentStamp(artifact.contentHash.value, hasContent = text != null),
        )
    }

    private fun indexedDocuments(): Map<String, Indexed> = database.query(
        "SELECT node_key, doc, content_hash, has_content FROM ${layout.documents}",
    ) { cursor ->
        val key = cursor.text()
        val doc = cursor.long()
        key to Indexed(doc, ContentStamp(cursor.text(), hasContent = cursor.int() == 1))
    }.toMap()

    private companion object {
        const val DELETE_CHUNK = 500
        const val INSERT_CHUNK = 32
    }
}
