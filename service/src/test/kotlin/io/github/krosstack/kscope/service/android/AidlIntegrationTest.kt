package io.github.krosstack.kscope.service.android

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.symbolOf
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scans a service module with AIDL contracts and an application that uses them, with the real analyzer worker. The
 * Kotlin and Java code refers to `IPing.Stub`, which exists only as a signature stub that the AIDL synthesizer makes.
 */
class AidlIntegrationTest {
    private val sample = SampleWorkspace("aidl")
    private val clientPath = "app/src/main/kotlin/sample/aidl/app/PingClient.kt"
    private val interfacePath = "svc/src/main/aidl/sample/aidl/svc/IPing.aidl"
    private val twoMethods = "package sample.aidl.svc;\n\n" +
        "import sample.aidl.svc.IPongCallback;\nimport sample.aidl.svc.Payload;\n\n" +
        "interface IPing {\n    const int MAX_PAYLOADS = 8;\n\n    void ping(in Payload payload);\n\n" +
        "    Payload latest();\n\n    oneway void watch(IPongCallback callback);\n\n    void reset();\n}\n"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun GraphReader.binds(subKind: String): List<Relationship> =
        relationships(RelationshipQuery(kinds = setOf(RelationKind.BINDS), subKind = subKind, limit = Int.MAX_VALUE))

    private fun GraphReader.symbolOf(node: NodeRef?): Symbol? = (node as? NodeRef.OfSymbol)?.let { symbol(it.id) }

    private fun GraphReader.targetOf(relationship: Relationship): Symbol? =
        symbolOf((relationship.resolution as? Resolution.Resolved)?.target)

    private fun GraphReader.pairs(subKind: String): List<Pair<String?, String?>> = binds(subKind)
        .map { symbolOf(it.from)?.qualifiedName to targetOf(it)?.qualifiedName }
        .sortedBy { "${it.first}${it.second}" }

    @Test
    fun `implementers in kotlin and java, objects and clients are bound to the contract`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertEquals(0, scan.diagnostics.warnings, "${scan.diagnostics.entries}")
            val graph = service.graph()
            assertEquals(
                listOf(
                    "sample.aidl.svc.IPing" to "sample.aidl.svc.JavaPing",
                    "sample.aidl.svc.IPing" to "sample.aidl.svc.PingBinder",
                    "sample.aidl.svc.IPongCallback" to "sample.aidl.svc.PingService.pongs",
                ),
                graph.pairs("implementer"),
            )
            val clients = graph.pairs("client")
            assertTrue("sample.aidl.svc.IPing" to "sample.aidl.app.PingClient" in clients, "$clients")
            assertTrue("sample.aidl.svc.IPongCallback" to "sample.aidl.svc.PingService" in clients, "$clients")
        }
    }

    @Test
    fun `an overriding method is bound to the contract method it implements, in an object by inference`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            assertEquals(
                listOf(
                    "sample.aidl.svc.IPing.latest" to "sample.aidl.svc.JavaPing.latest",
                    "sample.aidl.svc.IPing.latest" to "sample.aidl.svc.PingBinder.latest",
                    "sample.aidl.svc.IPing.ping" to "sample.aidl.svc.JavaPing.ping",
                    "sample.aidl.svc.IPing.ping" to "sample.aidl.svc.PingBinder.ping",
                    "sample.aidl.svc.IPing.watch" to "sample.aidl.svc.JavaPing.watch",
                    "sample.aidl.svc.IPing.watch" to "sample.aidl.svc.PingBinder.watch",
                    "sample.aidl.svc.IPongCallback.onPong" to "sample.aidl.svc.PingService.pongs",
                ),
                service.graph().pairs("override"),
            )
        }
    }

    @Test
    fun `the bindings rest on the compiler through the convention that generates the surface`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val implementers = graph.binds("implementer")
            assertTrue(implementers.all { it.evidenceClass == EvidenceClass.CONVENTION })
            assertTrue(implementers.all { (it.resolution as Resolution.Resolved).certainty == Certainty.EXACT })
            val interfaceSymbol = graph.symbols(SymbolQuery(kindId = "aidl.interface")).single { it.name == "IPing" }
            val generates = graph.outgoing(NodeRef.OfSymbol(interfaceSymbol.id), setOf(RelationKind.GENERATES))
            assertEquals(
                listOf("sample.aidl.svc.IPing", "sample.aidl.svc.IPing.Stub"),
                generates.map { graph.targetOf(it)?.qualifiedName }.sortedBy { it },
            )
            assertTrue(generates.all { it.evidenceClass == EvidenceClass.CONVENTION })
            assertTrue(graph.targetOf(generates.first())?.origin is SymbolOrigin.Generated)
        }
    }

    @Test
    fun `a parcelable is bound to the class of its name and the client call binds to the stub`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val parcelable = graph.binds("parcelable-class").single()
            assertEquals("sample.aidl.svc.Payload", graph.targetOf(parcelable)?.qualifiedName)
            assertEquals("kotlin.class", graph.targetOf(parcelable)?.kind?.id)
            assertEquals(EvidenceClass.TEXTUAL, parcelable.evidenceClass)
            val calls = graph.relationships(RelationshipQuery(kinds = setOf(RelationKind.CALLS), limit = Int.MAX_VALUE))
                .filter { it.site?.artifact?.path?.value == clientPath }
            assertTrue(calls.any { graph.targetOf(it)?.qualifiedName == "sample.aidl.svc.IPing.Stub.asInterface" })
        }
    }

    @Test
    fun `every resolved target in the workspace names a symbol, the generated ones included`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val targets = graph.relationships(RelationshipQuery(limit = Int.MAX_VALUE))
                .mapNotNull { (it.resolution as? Resolution.Resolved)?.target }
                .filterIsInstance<NodeRef.OfSymbol>().map { it.id }
                .filter { (it.identity() as? ParsedSymbolId.Valid)?.identity?.ecosystem == SymbolEcosystem.GRADLE }
            assertTrue(targets.size > 20, "Too few targets to say anything: ${targets.size}")
            assertEquals(emptyList(), targets.filter { graph.symbol(it) == null }.map { it.value })
            assertTrue(targets.any { graph.symbol(it)?.origin is SymbolOrigin.Generated })
        }
    }

    @Test
    fun `a new method in the aidl file reaches the stub and the bindings, and an incremental scan agrees`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            sample.write(interfacePath, twoMethods)
            val rescan = service.scan(verify = true)
            val verification = assertNotNull(rescan.verification)
            assertTrue(verification.matches, "${verification.differences}")
            val graph = service.graph()
            val generated = graph.symbols(SymbolQuery(qualifiedName = "sample.aidl.svc.IPing.reset"))
            assertEquals(listOf("aidl.method", "java.method"), generated.map { it.kind.id }.sorted())
        }
    }
}
