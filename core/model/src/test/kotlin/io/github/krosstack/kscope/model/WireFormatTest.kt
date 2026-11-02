package io.github.krosstack.kscope.model

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WireFormatTest {
    @Test
    fun `value classes are written as plain strings`() {
        assertEquals("\"repo-1\"", KscopeJson.encodeToString(RepositoryId.serializer(), Samples.repositoryId))
        assertEquals("\"a/b.kt\"", KscopeJson.encodeToString(RepoPath.serializer(), RepoPath("a/b.kt")))
    }

    @Test
    fun `enums are written as lowercase kebab names`() {
        assertEquals(
            "\"build-model\"",
            KscopeJson.encodeToString(EvidenceClass.serializer(), EvidenceClass.BUILD_MODEL),
        )
        assertEquals(
            "\"co-changes-with\"",
            KscopeJson.encodeToString(RelationKind.serializer(), RelationKind.CO_CHANGES_WITH),
        )
        assertEquals("\"exact\"", KscopeJson.encodeToString(Certainty.serializer(), Certainty.EXACT))
    }

    @Test
    fun `sealed hierarchies use stable lowercase discriminators`() {
        val symbolRef = KscopeJson.encodeToString(NodeRef.serializer(), NodeRef.OfExternal("maven:g:a"))
        assertEquals("{\"type\":\"external\",\"coordinate\":\"maven:g:a\"}", symbolRef)
        assertEquals("{\"type\":\"source\"}", KscopeJson.encodeToString(SymbolOrigin.serializer(), SymbolOrigin.Source))
        val notAnalysed = Resolution.NotAnalysed("h", ReasonCode("r.c"))
        assertContains(KscopeJson.encodeToString(Resolution.serializer(), notAnalysed), "\"type\":\"not-analysed\"")
    }

    @Test
    fun `facts are wrapped under their kind`() {
        val encoded = KscopeJson.encodeToString(Fact.serializer(), Fact.OfTarget(Samples.target))
        assertEquals(
            "{\"type\":\"target\",\"value\":{\"id\":\"repo-1//:core:model#target/jvm\"," +
                "\"module\":\"repo-1//:core:model\",\"name\":\"jvm\",\"platform\":\"jvm\"}}",
            encoded,
        )
    }

    @Test
    fun `absent values are written explicitly as null`() {
        assertEquals(
            "{\"sourceSet\":null,\"target\":null,\"variant\":null}",
            KscopeJson.encodeToString(Scope.serializer(), Scope()),
        )
    }

    @Test
    fun `attributes are written sorted by key`() {
        val encoded = KscopeJson.encodeToString(Relationship.serializer(), Samples.relationship)
        assertContains(encoded, "\"attributes\":{\"a\":\"2\",\"z\":\"1\"}")
    }

    @Test
    fun `module attributes are written sorted by key`() {
        val encoded = KscopeJson.encodeToString(Module.serializer(), Samples.module)
        val sorted = "\"attributes\":{\"android.buildFeatures.compose\":\"true\",\"android.compileSdk\":\"36\"}"
        assertContains(encoded, sorted)
    }

    @Test
    fun `sets are written in declaration order of their elements`() {
        val encoded = KscopeJson.encodeToString(Module.serializer(), Samples.module)
        assertContains(encoded, "\"traits\":[\"android-library\",\"kotlin-multiplatform\"]")
        val flags = KscopeJson.encodeToString(Symbol.serializer(), Samples.symbol)
        assertContains(flags, "\"flags\":[\"open\",\"suspend\"]")
    }

    @Test
    fun `output is compact so that it fits on one line`() {
        val encoded = KscopeJson.encodeToString(FactBatch.serializer(), Samples.batch)
        assertFalse(encoded.contains('\n'))
    }

    @Test
    fun `unknown fields are rejected`() {
        assertFailsWith<SerializationException> {
            KscopeJson.decodeFromString(
                TextRange.serializer(),
                "{\"startLine\":1,\"startColumn\":1,\"endLine\":1," +
                    "\"endColumn\":2,\"extra\":3}",
            )
        }
    }

    @Test
    fun `unknown discriminators are rejected`() {
        assertFailsWith<SerializationException> {
            KscopeJson.decodeFromString(NodeRef.serializer(), "{\"type\":\"OfSymbol\",\"id\":\"x\"}")
        }
    }
}
