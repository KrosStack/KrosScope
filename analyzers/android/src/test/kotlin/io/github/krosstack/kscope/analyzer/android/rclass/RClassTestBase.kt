package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.SyntheticSource

internal class SynthesisResult(val facts: List<Fact>, val sources: List<SyntheticSource>) {
    fun source(namespace: String) = sources.single { it.virtualPath.contains(namespace.replace('.', '/')) }
}

/** What the synthesizer tests share: the synthesizer and a way to run it over a sample tree. */
internal open class RClassTestBase {
    protected val synthesizer = RClassSynthesizer()

    protected fun run(tree: SampleTree = SampleTree("droid")): SynthesisResult {
        val (batches, sources) = tree.synthesize(synthesizer)
        return SynthesisResult(batches.facts(), sources)
    }
}
