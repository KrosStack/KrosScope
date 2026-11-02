package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Fact

/** What the producer tests share: the sample tree, the producer and the facts of a file. */
internal open class ProducerTestBase {
    protected val tree = SampleTree("droid")
    protected val producer = ResourceProducer()
    protected val strings = "app/src/main/res/values/strings.xml"
    protected val layout = "app/src/main/res/layout/activity_main.xml"

    protected fun factsOf(path: String, from: SampleTree = tree): List<Fact> =
        from.produce(producer) { it.ref.path.value == path }.facts()

    protected fun linesOf(path: String): List<String> = Render.lines(factsOf(path))
}
