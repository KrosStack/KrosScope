package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts

internal const val VIEW_BINDING_FEATURE_ON = "android.buildFeatures.viewBinding"

/** What the view binding tests share: the `ui` tree with view binding on for the application, and the synthesizer. */
internal open class ViewBindingTestBase {
    protected val synthesizer = ViewBindingSynthesizer()

    protected fun treeWithBinding(overrides: Map<String, String> = emptyMap()) = SampleTree(
        "ui",
        model = DroidModel.standard(appAttributes = mapOf(VIEW_BINDING_FEATURE_ON to "true")),
        overrides = overrides,
    )

    protected fun run(tree: SampleTree = treeWithBinding()): BindingResult {
        val (batches, sources) = tree.synthesizeModules(synthesizer)
        return BindingResult(batches.facts(), sources)
    }
}
