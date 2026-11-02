package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader

/**
 * What may have written the files that a copy source takes from: the npm packages whose output directory holds its
 * path and the Kotlin/JS and Kotlin/Wasm modules that put a bundle there, in the repository that holds the path. A copy
 * source whose path lies in no repository of the workspace has no producer.
 */
internal class BundleProducers(graph: GraphReader) {
    private val npm = NpmBundles(graph)
    private val modules = JsModuleBundles(graph)

    fun of(copy: Symbol): List<BundleProducer> {
        val repository = copy.attributes[WebKinds.COPY_REPOSITORY_ATTRIBUTE]
        val path = copy.attributes[WebKinds.COPY_PATH_ATTRIBUTE]
        return if (repository == null || path == null) {
            emptyList()
        } else {
            npm.producing(repository, path) + modules.producing(repository, path)
        }
    }
}
