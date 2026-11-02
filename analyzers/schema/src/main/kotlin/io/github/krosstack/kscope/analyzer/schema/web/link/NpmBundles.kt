package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private const val ALL = Int.MAX_VALUE

/**
 * The npm packages whose bundler writes to a directory, by repository. A package produces the files of a path when the
 * directory it writes to is the path or holds it. The match is by build model and probable when the config of the
 * bundler declares the directory, and by convention and heuristic when the default of the bundler applies.
 */
internal class NpmBundles(graph: GraphReader) {
    private val packages: List<Symbol> = graph.symbols(SymbolQuery(kindId = WebKinds.npmPackage.id, limit = ALL))
        .filter { WebKinds.OUTPUT_ATTRIBUTE in it.attributes }
        .sortedBy { it.id.value }

    fun producing(repository: String, path: String): List<BundleProducer> = packages
        .filter { it.nameLocation?.artifact?.repository?.value == repository }
        .filter { RepoPaths.isAtOrInside(path, it.attributes.getValue(WebKinds.OUTPUT_ATTRIBUTE)) }
        .map(::producerOf)

    private fun producerOf(symbol: Symbol): BundleProducer {
        val node = NodeRef.OfSymbol(symbol.id)
        return if (symbol.attributes[WebKinds.OUTPUT_DECLARED_ATTRIBUTE] == "true") {
            BundleProducer(node, EvidenceClass.BUILD_MODEL, Certainty.PROBABLE)
        } else {
            BundleProducer(node, EvidenceClass.CONVENTION, Certainty.HEURISTIC)
        }
    }
}
