package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.Render
import io.github.krosstack.kscope.analyzer.android.ResourceProducer
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.analyzer.android.rclass.RClassSynthesizer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.answerKey

internal class Linked(val references: List<SymbolicReference>, val relationships: List<Relationship>) {
    fun answer(key: String, inFile: String): Relationship {
        val matching = references.filter { it.key == key && it.site?.artifact?.path?.value.orEmpty().endsWith(inFile) }
        return relationships.single { it.answerKey() == matching.single().answerKey() }
    }
}

/** What the linker tests share: the graph made of the producer and synthesizer output, linked. */
internal open class LinkerTestBase {
    protected val linker = ResourceLinker()

    protected fun link(tree: SampleTree = SampleTree("droid"), extra: List<Fact> = emptyList()): Linked {
        val produced = tree.produce(ResourceProducer()).facts()
        val facts = produced + tree.synthesize(RClassSynthesizer()).first.facts() + extra
        val out = mutableListOf<Fact>()
        linker.link(FakeGraph(facts, tree.model)) { out += it.facts }
        val references = facts.filterIsInstance<Fact.OfSymbolicReference>().map { it.value }
            .filter { it.namespace in linker.namespaces }
        return Linked(references, out.filterIsInstance<Fact.OfRelationship>().map { it.value })
    }

    protected fun Relationship.target(): String = Render.node((resolution as Resolution.Resolved).target)

    protected fun Relationship.certainty(): Certainty = (resolution as Resolution.Resolved).certainty
}
