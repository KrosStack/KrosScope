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
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SERVICE_FILE = "svc/src/main/kotlin/Service.kt"
private const val CLIENT_FILE = "app/src/main/kotlin/Client.kt"
private const val AIDL_FILE = "svc/src/main/aidl/p/IFoo.aidl"

private fun contract(kind: String, name: String, line: Int, descriptor: SymbolDescriptor): Symbol =
    Graphs.symbol(AIDL_FILE, "p.$name", kind, line, descriptor)
        .copy(kind = SymbolKind(kind, SymbolCategory.CONTRACT), name = name)

private fun binds(from: Symbol, to: Symbol, subKind: String?, evidence: EvidenceClass = EvidenceClass.CONVENTION) =
    Relationship(
        from = NodeRef.OfSymbol(from.id),
        kind = RelationKind.BINDS,
        subKind = subKind,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
        evidenceClass = evidence,
        site = from.nameLocation,
        scope = Scope(),
    )

private fun aidlGold(id: String, from: GoldAddress, toFile: String, evidence: String = "generated-artifact") =
    GoldRelationship(id, "aidl", from, GoldAddress(file = toFile, line = 1), evidence)

class BoundaryContractScorersTest {
    private val fooDescriptor = SymbolDescriptor().namespace("aidl").namespace("p").type("IFoo")
    private val foo = contract("aidl.interface", "IFoo", 3, fooDescriptor)
    private val ping = contract("aidl.method", "ping", 5, fooDescriptor.method("ping"))
    private val parcel = contract("aidl.parcelable", "Payload", 3, SymbolDescriptor().namespace("aidl").type("Payload"))
    private val service = Graphs.symbol(SERVICE_FILE, "p.Service", "kotlin.class", 4, Graphs.type("Service"))
    private val binder =
        Graphs.symbol(SERVICE_FILE, "p.Service.binder", "kotlin.property", 6, Graphs.type("Service").term("b"))
    private val client = Graphs.symbol(CLIENT_FILE, "p.Client", "kotlin.class", 2, Graphs.type("Client"))
    private val payload = Graphs.symbol(SERVICE_FILE, "p.Payload", "kotlin.class", 9, Graphs.type("Payload"))

    private val fooAddress =
        GoldAddress(module = ":svc", file = AIDL_FILE, kind = "aidl.interface", name = "IFoo", line = 3)
    private val pingAddress = fooAddress.copy(kind = "aidl.method", name = "ping", line = 5)
    private val parcelAddress = fooAddress.copy(kind = "aidl.parcelable", name = "Payload")

    private fun graph(vararg relationships: Relationship) = FakeGraph(
        symbols = listOf(foo, ping, parcel, service, binder, client, payload),
        artifacts = listOf(Graphs.artifact(AIDL_FILE, "aidl.source"), Graphs.artifact(SERVICE_FILE)),
        relationships = relationships.toList(),
    )

    private fun scorer(graph: FakeGraph) = AidlScorer(GraphIndex(graph), GoldAddressResolver(graph))

    @Test
    fun `an implementer is matched by the file of the object, which is a property when it has no symbol`() {
        val gold = listOf(aidlGold("i:1", fooAddress, SERVICE_FILE))
        val score = scorer(graph(binds(foo, binder, "implementer"))).implementers(gold)
        assertEquals(1, score.score?.matched)
        assertEquals(1.0, score.score?.precision)
        assertEquals(1.0, score.score?.recall)
        assertEquals(0, score.evidenceMismatches)
        assertEquals(mapOf("convention" to 1), score.acceptedEvidence)
    }

    @Test
    fun `a client that gold does not list is an extra and one that the graph lacks is a miss`() {
        val gold = listOf(aidlGold("c:1", fooAddress, CLIENT_FILE), aidlGold("c:2", fooAddress, "app/Other.kt"))
        val score = scorer(graph(binds(foo, client, "client"), binds(foo, service, "client"))).clients(gold).score
        assertEquals(1, score?.matched)
        assertEquals(listOf("c:2 -> app/Other.kt"), score?.misses)
        assertEquals(listOf("IFoo -> svc/src/main/kotlin/Service.kt"), score?.extras)
    }

    @Test
    fun `an override that the graph does not have is a miss`() {
        val score = scorer(graph()).overrides(listOf(aidlGold("o:1", pingAddress, SERVICE_FILE)))
        assertEquals(0.0, score.score?.recall)
        assertEquals(listOf("o:1 -> $SERVICE_FILE"), score.score?.misses)
    }

    @Test
    fun `a parcelable bound by name has textual evidence and needs no policy`() {
        val gold = listOf(aidlGold("p:1", parcelAddress, SERVICE_FILE, "textual"))
        val score = scorer(graph(binds(parcel, payload, "parcelable-class", EvidenceClass.TEXTUAL))).parcelables(gold)
        assertEquals(1.0, score.score?.recall)
        assertEquals(0, score.evidenceMismatches)
        assertEquals(emptyMap(), score.acceptedEvidence)
    }

    @Test
    fun `only the sub-kind of the class counts`() {
        val gold = listOf(aidlGold("i:1", fooAddress, CLIENT_FILE))
        val score = scorer(graph(binds(foo, client, "client"))).implementers(gold)
        assertEquals(0, score.score?.matched)
        assertEquals(1, score.score?.misses?.size)
    }

    @Test
    fun `a contract that gold names but the graph does not have is reported as unmapped`() {
        val ghost = fooAddress.copy(name = "IGhost")
        val score = scorer(graph()).implementers(listOf(aidlGold("i:9", ghost, SERVICE_FILE)))
        assertEquals(1, score.unmapped.size)
        assertTrue(score.unmapped.single().startsWith("i:9: no-matching-symbol"))
        assertEquals(0, score.score?.matched)
    }
}
