package io.github.krosstack.kscope.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CanonicalFactsTest {
    private val otherBatch = Samples.batch.copy(
        producer = ProducerRef("android-manifest", "2.1.0"),
        owner = OwnershipUnit(OwnershipKind.MODULE, "repo-1//:app"),
        facts = Samples.facts.reversed(),
    )

    @Test
    fun `shuffled facts and batches serialize to identical bytes`() {
        val expected = CanonicalFacts.toJson(listOf(Samples.batch, otherBatch))
        repeat(SHUFFLE_ROUNDS) { seed ->
            val random = Random(seed)
            val shuffled = listOf(Samples.batch, otherBatch).map { it.copy(facts = it.facts.shuffled(random)) }
            assertEquals(expected, CanonicalFacts.toJson(shuffled.shuffled(random)))
        }
    }

    @Test
    fun `batches are ordered by producer then owner`() {
        val sorted = CanonicalFacts.sorted(listOf(Samples.batch, otherBatch))
        assertEquals(listOf("android-manifest", "kotlin-structure"), sorted.map { it.producer.id })
    }

    @Test
    fun `facts are ordered by type before identifier`() {
        val sorted = CanonicalFacts.sorted(Samples.batch.copy(facts = Samples.facts.reversed())).facts
        val types = sorted.map { KscopeJson.encodeToString(Fact.serializer(), it).substringBefore(",\"value\"") }
        assertEquals(types.sorted(), types)
    }

    @Test
    fun `facts of one type are ordered by identifier`() {
        val first = Fact.OfModule(Samples.module.copy(id = ModuleId("a//:one")))
        val second = Fact.OfModule(Samples.module.copy(id = ModuleId("a//:two")))
        val sorted = CanonicalFacts.sorted(Samples.batch.copy(facts = listOf(second, first))).facts
        assertEquals(listOf(first, second), sorted)
    }

    @Test
    fun `relationships that differ only in their target keep a fixed order`() {
        val toExternal = Samples.relationship.copy(resolution = Samples.resolutions[0])
        val unresolved = Samples.relationship.copy(resolution = Samples.resolutions[1])
        val facts = listOf(Fact.OfRelationship(unresolved), Fact.OfRelationship(toExternal))
        val forward = CanonicalFacts.sorted(Samples.batch.copy(facts = facts)).facts
        val backward = CanonicalFacts.sorted(Samples.batch.copy(facts = facts.reversed())).facts
        assertEquals(forward, backward)
    }

    @Test
    fun `sorting is idempotent`() {
        val once = CanonicalFacts.sorted(listOf(Samples.batch, otherBatch))
        assertEquals(once, CanonicalFacts.sorted(once))
    }

    @Test
    fun `different content produces different bytes`() {
        val changed = Samples.batch.copy(inputFingerprint = Fingerprint("0".repeat(64)))
        assertNotEquals(CanonicalFacts.toJson(listOf(Samples.batch)), CanonicalFacts.toJson(listOf(changed)))
    }

    private companion object {
        const val SHUFFLE_ROUNDS = 20
    }
}
