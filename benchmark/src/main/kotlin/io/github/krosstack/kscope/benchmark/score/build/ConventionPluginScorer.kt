package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.MappedClass
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.expectedItems
import io.github.krosstack.kscope.benchmark.score.pairKey
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.benchmark.score.unmappedMisses
import io.github.krosstack.kscope.model.NodeRef

/** Every convention plugin to each module that applies it. */
internal class ConventionPluginScorer(index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val scriptModules = ScriptModules(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        val matching = Matching.of(expected, produced())
        return scoreOf("convention_plugin_to_module", gold, mapped, matching, checksEvidence = false)
    }

    private fun produced(): List<Item> = scriptModules.conventionScripts().flatMap { script ->
        scriptModules.modulesOf(script).map { module ->
            val plugin = NodeRef.OfArtifact(script.ref)
            Item(pairKey(plugin, NodeRef.OfModule(module.id)), "${script.ref.path.value} -> ${module.id.value}")
        }
    }
}
