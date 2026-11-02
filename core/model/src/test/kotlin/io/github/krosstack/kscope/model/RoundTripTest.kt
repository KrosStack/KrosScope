package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundTripTest {
    @Test
    fun `every model type survives a JSON round trip`() {
        roundTripCases.forEach { assertSurvivesRoundTrip(it) }
    }

    @Test
    fun `encoding the decoded value reproduces the same bytes`() {
        roundTripCases.forEach { assertStableEncoding(it) }
    }

    @Test
    fun `every sealed variant is covered by a round trip case`() {
        val covered = roundTripCases.map { it.name }.toSet()
        val variants = listOf(
            "NodeRef OfSymbol", "NodeRef OfArtifact", "NodeRef OfModule", "NodeRef OfSourceSet", "NodeRef OfExternal",
            "Resolution Resolved", "Resolution Unresolved", "Resolution NotAnalysed",
            "SymbolOrigin Source", "SymbolOrigin Generated", "SymbolOrigin Synthetic", "SymbolOrigin Library",
            "Fact OfRepository", "Fact OfBuildRoot", "Fact OfModule", "Fact OfTarget", "Fact OfVariant",
            "Fact OfSourceSet", "Fact OfArtifact", "Fact OfSymbol", "Fact OfRelationship",
            "Fact OfSymbolicReference", "Fact OfMetric", "Fact OfDiagnostic",
        )
        assertTrue(covered.containsAll(variants), "Missing: ${variants - covered}")
    }

    private fun <T> assertSurvivesRoundTrip(case: RoundTripCase<T>) {
        val encoded = KscopeJson.encodeToString(case.serializer, case.value)
        assertEquals(case.value, KscopeJson.decodeFromString(case.serializer, encoded), case.name)
    }

    private fun <T> assertStableEncoding(case: RoundTripCase<T>) {
        val encoded = KscopeJson.encodeToString(case.serializer, case.value)
        val decoded = KscopeJson.decodeFromString(case.serializer, encoded)
        assertEquals(encoded, KscopeJson.encodeToString(case.serializer, decoded), case.name)
    }
}
