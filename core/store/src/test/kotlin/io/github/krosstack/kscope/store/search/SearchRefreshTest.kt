package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.search.SearchContent
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.TempDirectory
import io.github.krosstack.kscope.store.generator.FactGenerator
import io.github.krosstack.kscope.store.openedStore
import io.github.krosstack.kscope.store.replaceAll
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SearchRefreshTest {
    /** Content that depends on nothing but the artifact, so that any two refreshes read the same text. */
    private object EchoContent : SearchContent {
        override fun isIndexed(artifact: Artifact): Boolean =
            artifact.generated == null && artifact.type.category != ArtifactCategory.OTHER

        override fun textOf(artifact: Artifact): String =
            "echo ${artifact.ref.path.value} ${artifact.contentHash.value}"
    }

    @Test
    fun `random changes refreshed at random moments give the documents of a fresh index`() {
        var documents = 0
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val generator = FactGenerator(seed)
            SqliteGraphStore.inMemory().use { incremental ->
                repeat(STEPS) {
                    change(incremental, generator, random)
                    if (random.nextBoolean()) incremental.refreshSearchIndex(EchoContent)
                }
                incremental.refreshSearchIndex(EchoContent)
                SqliteGraphStore.inMemory().use { fresh ->
                    fresh.replaceAll(incremental.batches())
                    fresh.refreshSearchIndex(EchoContent)
                    assertEquals(fresh.searchDocuments(), incremental.searchDocuments(), "seed $seed")
                    documents += incremental.searchDocuments().size
                }
            }
        }
        assertTrue(documents > SEEDS, "The random changes left $documents documents, so the check proves little")
    }

    private fun change(store: GraphStore, generator: FactGenerator, random: Random) {
        val units = store.units()
        if (units.isNotEmpty() && random.nextInt(REMOVAL_ODDS) == 0) {
            val unit = units.random(random)
            store.remove(unit.producer, unit.owner)
        } else {
            val owner = OwnershipUnit(OwnershipKind.ARTIFACT, "unit-${random.nextInt(OWNERS)}")
            store.replace(generator.batch(owner).copy(producer = Builders.producer))
        }
    }

    @Test
    fun `a refresh with nothing to do reads no content and leaves the documents as they were`() {
        SqliteGraphStore.inMemory().use { store ->
            store.addSymbols("unit", searchSymbol("a", "Alpha"), searchSymbol("b", "Beta"))
            store.addArtifacts(sourceArtifact("src/Alpha.kt"), sourceArtifact("src/Beta.kt"))
            val content = FakeContent(mapOf("src/Alpha.kt" to "class Alpha", "src/Beta.kt" to "class Beta"))
            store.refreshSearchIndex(content)
            val documents = store.searchDocuments()
            val reads = content.reads
            repeat(2) { store.refreshSearchIndex(content) }
            assertEquals(reads, content.reads)
            assertEquals(documents, store.searchDocuments())
        }
    }

    @Test
    fun `replacing a unit with an identical batch leaves the documents as they were`() {
        SqliteGraphStore.inMemory().use { store ->
            val batch = Builders.symbols("unit", searchSymbol("a", "Alpha"), searchSymbol("b", "Beta"))
            store.replace(batch)
            store.refreshSearchIndex(FakeContent())
            val documents = store.searchDocuments()
            store.replace(batch)
            store.refreshSearchIndex(FakeContent())
            assertEquals(documents, store.searchDocuments())
        }
    }

    @Test
    fun `the document of a symbol that several units declare follows the declaration that readers see`() {
        SqliteGraphStore.inMemory().use { store ->
            val generated = searchSymbol("shared", "GeneratedName", origin = SymbolOrigin.Generated("gen", null))
            store.addSymbols("generated-unit", generated)
            store.addSymbols("source-unit", generated.copy(name = "SourceName", origin = SymbolOrigin.Source))
            store.refreshSearchIndex(FakeContent())
            assertEquals(listOf("shared"), store.symbolHits("sourcename").symbolIds())
            assertEquals(emptyList(), store.symbolHits("generatedname").symbolIds())
            store.remove(Builders.producer, OwnershipUnit(OwnershipKind.ARTIFACT, "source-unit"))
            store.refreshSearchIndex(FakeContent())
            assertEquals(listOf("shared"), store.symbolHits("generatedname").symbolIds())
            assertEquals(emptyList(), store.symbolHits("sourcename").symbolIds())
        }
    }

    @Test
    fun `a changed symbol is found by its new words and no longer by its old ones`() {
        SqliteGraphStore.inMemory().use { store ->
            store.addSymbols("unit", searchSymbol("a", "Alpha").documented("Counts apples."))
            store.refreshSearchIndex(FakeContent())
            store.addSymbols("unit", searchSymbol("a", "Alpha").documented("Counts oranges."))
            store.refreshSearchIndex(FakeContent())
            assertEquals(emptyList(), store.symbolHits("apples").symbolIds())
            assertEquals(listOf("a"), store.symbolHits("oranges").symbolIds())
        }
    }

    @Test
    fun `symbols marked before a refresh survive a reopened file store`() {
        TempDirectory().use { directory ->
            val file = directory.path.resolve("index.db")
            openedStore(file).use { it.addSymbols("unit", searchSymbol("a", "Alpha")) }
            openedStore(file).use { store ->
                store.refreshSearchIndex(FakeContent())
                assertEquals(listOf("a"), store.symbolHits("alpha").symbolIds())
            }
            openedStore(file).use { store ->
                assertNotEquals(emptyList(), store.searchDocuments())
                assertEquals(listOf("a"), store.symbolHits("alpha").symbolIds())
            }
        }
    }

    private companion object {
        const val SEEDS = 12
        const val STEPS = 14
        const val OWNERS = 5
        const val REMOVAL_ODDS = 4
    }
}
