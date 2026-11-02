package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SymbolicReferenceTest {
    private val reference = Samples.symbolicReference

    @Test
    fun `a symbolic reference is written under its own discriminator with its fields in declaration order`() {
        val encoded = KscopeJson.encodeToString(Fact.serializer(), Fact.OfSymbolicReference(reference))
        assertContains(encoded, "{\"type\":\"symbolic-reference\",\"value\":{\"from\":{\"type\":\"artifact\"")
        val order = listOf("kind", "subKind", "namespace", "key", "site", "scope", "evidenceClass").map { "\"$it\"" }
        val positions = order.map { encoded.indexOf(it, startIndex = encoded.indexOf("\"value\"")) }
        assertEquals(positions.sorted(), positions)
        assertContains(encoded, "\"namespace\":\"android.resource\",\"key\":\"string/app_name\"")
    }

    @Test
    fun `symbolic references in a batch are ordered by their canonical form whatever the input order`() {
        val first = reference.copy(key = "color/brand")
        val second = reference.copy(key = "string/app_name")
        val forward = sortedBatchOf(first, second)
        val backward = sortedBatchOf(second, first)
        assertEquals(forward, backward)
        assertEquals(listOf(first, second), forward.facts.map { (it as Fact.OfSymbolicReference).value })
    }

    @Test
    fun `a relationship built as the answer carries everything of the reference and the given resolution`() {
        val target = Resolution.Resolved(NodeRef.OfSymbol(Samples.symbolId), Certainty.PROBABLE)
        val answer = reference.answeredBy(target, attributes = mapOf("candidates" to "2"))
        assertEquals(reference.from, answer.from)
        assertEquals(reference.kind, answer.kind)
        assertEquals(reference.subKind, answer.subKind)
        assertEquals(reference.site, answer.site)
        assertEquals(reference.scope, answer.scope)
        assertEquals(reference.evidenceClass, answer.evidenceClass)
        assertEquals(target, answer.resolution)
        assertEquals(reference.answerKey(), answer.answerKey())
    }

    @Test
    fun `references that differ in their site or kind are answered by different relationships`() {
        val other = reference.copy(site = reference.site?.copy(range = Samples.range.copy(startLine = 4)))
        assertNotEquals(reference.answerKey(), other.answerKey())
        assertNotEquals(reference.answerKey(), reference.copy(kind = RelationKind.CALLS).answerKey())
        assertNotEquals(reference.answerKey(), reference.copy(subKind = null).answerKey())
    }

    private fun sortedBatchOf(vararg references: SymbolicReference) =
        CanonicalFacts.sorted(Samples.batch.copy(facts = references.map { Fact.OfSymbolicReference(it) }))
}
