package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.web.copy.CopySourceProducer
import io.github.krosstack.kscope.analyzer.schema.web.link.BundleConsumerLinker
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactProducer

/**
 * The capabilities for web bundles: the TypeScript and JavaScript sources, the npm packages with their bundler
 * configs, the files that a build of a package leaves, which are linked to their sources by their source maps, and the
 * paths that Gradle tasks copy from, which are linked to the packages and modules that build the bundle in them.
 */
public object WebBundleCapabilities {
    public fun detectors(): List<ArtifactDetector> = listOf(
        BundlerConfigDetector(),
        NpmManifestDetector(),
        ScriptSourceDetector.typescript(),
        ScriptSourceDetector.javascript(),
        SourceMapDetector(),
    )

    public fun producers(): List<FactProducer> = listOf(CopySourceProducer(), WebBundleProducer())

    public fun linkers(): List<BoundaryLinker> = listOf(BundleConsumerLinker())
}
