package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.SymbolId

/**
 * A spec file that an input of a generator reaches: the artifact, where it is placed, and the id of its symbol.
 * [virtualName] tells it apart from the spec files of other repositories in the name of a synthetic source.
 */
internal class SpecTarget(val artifact: Artifact, val placement: Placement, val symbol: SymbolId) {
    val virtualName: String
        get() = "${artifact.ref.repository.value}_${artifact.ref.path.value.replace('/', '_')}"
}
