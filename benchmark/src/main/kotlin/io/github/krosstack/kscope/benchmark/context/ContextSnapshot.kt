package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpus
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat

internal object ContextSnapshot {
    fun fingerprint(graph: GraphReader, corpus: LocalizationCorpus): String {
        val digest = SnapshotDigest()
        structure(digest, graph.structure())
        digest.items("artifacts", graph.artifacts())
        digest.items("symbols", graph.symbols(SymbolQuery(limit = Int.MAX_VALUE)))
        digest.items("relationships", graph.relationships(RelationshipQuery(limit = Int.MAX_VALUE)))
        val coverage = graph.coverage()
        digest.add("coverage", KscopeJson.encodeToString(coverage))
        coverage.metricsByName.keys.sorted().forEach { name -> digest.items("metrics:$name", graph.metrics(name)) }
        digest.add("corpus-summary", KscopeJson.encodeToString(corpus.summary))
        corpus.documents.toSortedMap().forEach { (path, text) -> digest.add("corpus:$path", contextFingerprint(text)) }
        return digest.finish()
    }

    private fun structure(digest: SnapshotDigest, structure: WorkspaceStructure) {
        digest.items("repositories", structure.repositories)
        digest.items("build-roots", structure.buildRoots)
        digest.items("modules", structure.modules)
        digest.items("targets", structure.targets)
        digest.items("variants", structure.variants)
        digest.items("source-sets", structure.sourceSets)
    }
}

private class SnapshotDigest {
    private val digest = MessageDigest.getInstance("SHA-256")

    inline fun <reified T> items(label: String, values: List<T>) {
        add("$label-count", values.size.toString())
        values.forEach { add(label, KscopeJson.encodeToString(it)) }
    }

    fun add(label: String, text: String) {
        bytes(label.toByteArray(Charsets.UTF_8))
        bytes(text.toByteArray(Charsets.UTF_8))
    }

    fun finish(): String = HexFormat.of().formatHex(digest.digest())

    private fun bytes(value: ByteArray) {
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
        digest.update(value)
    }
}
