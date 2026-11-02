package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.FlowReader
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

private const val BYTE_ORDER_MARK = "\uFEFF"

/** Claims `.map` files whose first bytes open a JSON object with a `version` key, as a source map does. */
internal class SourceMapDetector : ArtifactDetector {
    override val id: String = WebArtifactTypes.sourceMap.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val isMap = candidate.path.value.lowercase().endsWith(SUFFIX)
        return WebArtifactTypes.sourceMap.takeIf { isMap && declaresVersion(candidate.head()) }
    }

    private fun declaresVersion(head: ByteArray): Boolean {
        val text = head.toString(Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK).trimStart()
        return text.startsWith("{") && (FlowReader(text, 0).read() as? DocMapping)?.entry("version") != null
    }

    companion object {
        const val SUFFIX = ".map"
    }
}
