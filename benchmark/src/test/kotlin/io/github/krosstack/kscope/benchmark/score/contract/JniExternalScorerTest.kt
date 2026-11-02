package io.github.krosstack.kscope.benchmark.score.contract

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

private const val NAME = "Java_p_Checksum_crc32"
private const val NATIVE_FILE = "bridge/src/main/cpp/checksum.c"

class JniExternalScorerTest {
    private val external = Graphs.symbol(
        "bridge/Checksum.kt",
        "p.Checksum.crc32",
        "kotlin.function",
        8,
        Graphs.method("Checksum", "crc32", "ByteArray"),
    )
    private val contract = Graphs.symbol("", NAME, "jni.function", 1, SymbolDescriptor().namespace("jni").term(NAME))
        .copy(declaration = null, nameLocation = null)
    private val native = Graphs.symbol(
        NATIVE_FILE,
        NAME,
        "jni.native-function",
        13,
        SymbolDescriptor().meta(NATIVE_FILE).meta("definition").term(NAME),
    )

    private val generates = Relationship(
        from = NodeRef.OfSymbol(external.id),
        kind = RelationKind.GENERATES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(contract.id), Certainty.EXACT),
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = Scope(),
    )

    private val gold = listOf(
        GoldRelationship(
            id = "j:1",
            relationshipClass = "jni_external_to_native",
            from = GoldAddress(
                module = ":core",
                sourceSet = "main",
                fqName = "p.Checksum.crc32",
                kind = "kotlin.external",
                file = "bridge/Checksum.kt",
                line = 8,
            ),
            to = GoldAddress(module = ":core", file = NATIVE_FILE, kind = "jni.function", name = NAME, line = 13),
            expectedEvidence = "convention",
        ),
    )

    private fun binding(resolution: Resolution) = Relationship(
        from = NodeRef.OfSymbol(contract.id),
        kind = RelationKind.BINDS,
        subKind = null,
        resolution = resolution,
        evidenceClass = EvidenceClass.CONVENTION,
        site = null,
        scope = Scope(),
    )

    private fun score(vararg relationships: Relationship, extra: List<Symbol> = emptyList()) = FakeGraph(
        symbols = listOf(external, contract, native) + extra,
        artifacts = listOf(Graphs.artifact("bridge/Checksum.kt"), Graphs.artifact(NATIVE_FILE, "native.source")),
        relationships = relationships.toList(),
    ).let { JniExternalScorer(GraphIndex(it), GoldAddressResolver(it)).score(gold) }

    @Test
    fun `a declaration is matched to the native function its contract is bound to`() {
        val bound = binding(Resolution.Resolved(NodeRef.OfSymbol(native.id), Certainty.EXACT))
        val score = score(generates, bound)
        assertEquals(1, score.score?.matched)
        assertEquals(1.0, score.score?.precision)
        assertEquals(0, score.evidenceMismatches)
    }

    @Test
    fun `a contract that is not bound to a function is a miss`() {
        val unresolved = binding(Resolution.Unresolved(NAME, ReasonCode("jni.definition-not-found")))
        val score = score(generates, unresolved)
        assertEquals(0, score.score?.matched)
        assertEquals(1, score.score?.misses?.size)
        assertEquals(emptyList(), score.score?.extras)
    }

    @Test
    fun `a binding of a contract without a declaration is not an edge to score`() {
        val bound = binding(Resolution.Resolved(NodeRef.OfSymbol(native.id), Certainty.EXACT))
        assertEquals(0, score(bound).score?.matched)
    }
}
