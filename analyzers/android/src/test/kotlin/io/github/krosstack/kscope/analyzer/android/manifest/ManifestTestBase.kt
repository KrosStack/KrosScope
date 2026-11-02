package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolicReference

/** What the manifest tests share: the `ui` tree, its manifests and the facts the manifest producer makes of one. */
internal open class ManifestTestBase {
    protected val tree = SampleTree("ui")
    protected val main = "app/src/main/AndroidManifest.xml"
    protected val debug = "app/src/debug/AndroidManifest.xml"

    protected fun factsOf(path: String = main, from: SampleTree = tree): List<Fact> =
        from.produce(ManifestProducer()) { it.ref.path.value == path }.facts()

    protected fun symbolsOf(path: String = main, from: SampleTree = tree): List<Symbol> =
        factsOf(path, from).filterIsInstance<Fact.OfSymbol>().map { it.value }

    protected fun referencesOf(path: String, namespace: String): List<SymbolicReference> =
        factsOf(path).filterIsInstance<Fact.OfSymbolicReference>().map { it.value }
            .filter { it.namespace == namespace }
}
