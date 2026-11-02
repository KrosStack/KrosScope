package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType

/**
 * The artifact types of web bundles. Neither the bundler configs nor the manifests are build logic of the build model,
 * so they are configuration; a source map is data that only this analyzer reads.
 */
internal object WebArtifactTypes {
    val bundlerConfig = ArtifactType("bundler.config", ArtifactCategory.CONFIG)
    val npmManifest = ArtifactType("npm.manifest", ArtifactCategory.CONFIG)
    val typescript = ArtifactType("typescript.source", ArtifactCategory.SOURCE)
    val javascript = ArtifactType("javascript.source", ArtifactCategory.SOURCE)
    val sourceMap = ArtifactType("source-map", ArtifactCategory.OTHER)

    val scripts = setOf(typescript, javascript)
    val all = setOf(bundlerConfig, npmManifest, typescript, javascript, sourceMap)
}
