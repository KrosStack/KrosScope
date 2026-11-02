package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ProducerBehaviorTest : ProducerTestBase() {

    @Test
    fun `a file that cannot be read to its end keeps what came before and warns`() {
        val lines = linesOf("app/src/main/res/values/broken.xml")
        assertEquals(
            listOf(
                "symbol android.resource.string :app@main:android/res/string/before_error. 3:19-3:31",
                "symbol android.resource.string :app@main:android/res/string/unclosed. 4:19-4:27",
                "diagnostic WARNING android.resource.xml-malformed",
            ),
            lines,
        )
    }

    @Test
    fun `a folder that is no resource type only gets a note`() {
        assertEquals(
            listOf("diagnostic INFO android.resource.unknown-type"),
            linesOf("app/src/main/res/broken/note.txt"),
        )
    }

    @Test
    fun `without a namespace the symbols have no qualified name`() {
        val bare = SampleTree("droid", DroidModel.standard(appNamespace = null))
        val symbol = factsOf(strings, bare).filterIsInstance<Fact.OfSymbol>().first().value
        assertEquals(null, symbol.qualifiedName)
    }

    @Test
    fun `a file that no module contains is noted and a file that cannot be read is a warning`() {
        val artifact = tree.artifact(strings)
        val orphan = AnalysisUnit(OwnershipUnit(OwnershipKind.ARTIFACT, "orphan"), listOf(artifact), null, null)
        val orphanFacts = mutableListOf<Fact>()
        producer.produce(tree.requestFor(listOf(orphan))) { orphanFacts += it.facts }
        assertEquals(listOf("diagnostic INFO android.resource.module-unknown"), Render.lines(orphanFacts))

        val unreadable = object : WorkspaceFiles by tree.files {
            override fun read(ref: ArtifactRef): ByteArray = throw IOException("gone")
        }
        val request = tree.requestFor(listOf(tree.unitOf(artifact))).copy(files = unreadable)
        val facts = mutableListOf<Fact>()
        producer.produce(request) { facts += it.facts }
        assertEquals(listOf("diagnostic WARNING android.resource.unreadable"), Render.lines(facts))
    }

    @Test
    fun `every symbolic reference starts at a symbol of its own batch and symbols are unique per batch`() {
        tree.produce(producer).forEach { batch ->
            val symbols = batch.facts.filterIsInstance<Fact.OfSymbol>().map { it.value.id }
            assertEquals(symbols.distinct(), symbols, batch.owner.key)
            batch.facts.filterIsInstance<Fact.OfSymbolicReference>().forEach {
                val from = it.value.from
                assertTrue(from is NodeRef.OfSymbol && from.id in symbols, batch.owner.key)
            }
        }
    }

    @Test
    fun `the facts of a values file and of a layout match the golden output`() {
        val values = tree.produce(producer) { it.ref.path.value == strings }
        val layouts = tree.produce(producer) { it.ref.path.value == layout }
        Golden.assertMatches("producer-values.jsonl", Golden.facts(values))
        Golden.assertMatches("producer-layout.jsonl", Golden.facts(layouts))
    }

    @Test
    fun `producing twice gives the same facts and the descriptor names what is consumed`() {
        assertEquals(Golden.facts(tree.produce(producer)), Golden.facts(tree.produce(producer)))
        assertEquals(OwnershipKind.ARTIFACT, producer.descriptor.granularity)
        assertEquals(AndroidArtifactTypes.resources, producer.descriptor.consumes)
    }
}
