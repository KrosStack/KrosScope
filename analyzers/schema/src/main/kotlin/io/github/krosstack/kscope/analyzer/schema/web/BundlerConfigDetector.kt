package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

private val configName = Regex("^(vite|webpack|rollup)\\.config\\.(js|mjs|cjs|ts|mts|cts)$")

/**
 * Claims the config files of the bundlers Vite, webpack and Rollup. It wins over the detectors of scripts for the same
 * files, because its id comes first.
 */
internal class BundlerConfigDetector : ArtifactDetector {
    override val id: String = WebArtifactTypes.bundlerConfig.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? =
        WebArtifactTypes.bundlerConfig.takeIf { bundlerIdOf(candidate.path.value) != null }

    companion object {
        fun bundlerIdOf(path: String): String? =
            configName.matchEntire(path.substringAfterLast('/').lowercase())?.groupValues?.get(1)
    }
}
