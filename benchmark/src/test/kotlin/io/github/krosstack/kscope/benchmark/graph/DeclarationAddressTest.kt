package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeclarationAddressTest {
    private val file = "core/src/main/kotlin/p/Service.kt"
    private val java = "core/src/main/java/p/Legacy.java"

    private val kotlin = "kotlin.function"
    private val service = Graphs.symbol(file, "p.Service", "kotlin.class", 3, Graphs.type("Service"))
    private val runString = Graphs.symbol(file, "p.Service.run", kotlin, 5, Graphs.method("Service", "run", "String"))
    private val runInt = Graphs.symbol(file, "p.Service.run", kotlin, 7, Graphs.method("Service", "run", "Int"))
    private val javaMethod = "java.method"
    private val padString = Graphs.symbol(java, "p.Legacy.pad", javaMethod, 4, Graphs.method("Legacy", "pad", "String"))
    private val padLong = Graphs.symbol(java, "p.Legacy.pad", javaMethod, 4, Graphs.method("Legacy", "pad", "Long"))
    private val constructor =
        Graphs.symbol(file, "p.Service.<init>", "kotlin.constructor", 3, Graphs.method("Service", "<init>", "A"))
    private val plain = Graphs.symbol(java, "p.Legacy", "java.class", 3, Graphs.type("Legacy"))

    private val graph = FakeGraph(
        symbols = listOf(service, runString, runInt, padString, padLong, constructor, plain),
        artifacts = listOf(Graphs.artifact(file), Graphs.artifact(java, "java.source")),
    )
    private val resolver = GoldAddressResolver(graph)

    private fun resolved(address: GoldAddress): Symbol {
        val found = assertIs<AddressResolution.Found>(resolver.resolve(address))
        return graph.symbol((found.node as NodeRef.OfSymbol).id) ?: error("The resolved symbol is not in the graph")
    }

    private fun address(fqName: String, line: Int, kind: String? = null, path: String = file) =
        GoldAddress(fqName = fqName, line = line, file = path, kind = kind)

    @Test
    fun `a declaration is found by file qualified name and line`() {
        assertEquals(service, resolved(address("p.Service", 3)))
        assertEquals(runString, resolved(address("p.Service.run", 5)))
        assertEquals(runInt, resolved(address("p.Service.run", 7)))
    }

    @Test
    fun `overloads on one line are told apart by their parameter types`() {
        val longPad = address("p.Legacy.pad", 4, path = java).copy(signature = "(java.lang.Long)")
        assertEquals(padLong, resolved(longPad))
    }

    @Test
    fun `an overload that stays ambiguous is reported and not guessed`() {
        val unmapped = assertIs<AddressResolution.Unmapped>(resolver.resolve(address("p.Legacy.pad", 4, path = java)))
        assertEquals(UnmappedReason.AMBIGUOUS, unmapped.reason)
    }

    @Test
    fun `the only candidate is accepted when its line differs and says so`() {
        val found = assertIs<AddressResolution.Found>(resolver.resolve(address("p.Service", 9)))
        assertEquals(true, found.approximateLine)
    }

    @Test
    fun `a constructor is the constructor symbol when there is one and the class when there is not`() {
        assertEquals(constructor, resolved(address("p.Service", 3, kind = "constructor")))
        assertEquals(plain, resolved(address("p.Legacy", 3, kind = "constructor", path = java)))
    }

    @Test
    fun `a member the compiler made is attributed to its class`() {
        val generated = GoldAddress(fqName = "p.Service.copy", file = file, line = 3, compilerGenerated = true)
        assertEquals(service, resolved(generated))
    }

    @Test
    fun `a file facade is the Kotlin file that holds its declarations`() {
        val facade = GoldAddress(fqName = "p.ServiceKt", file = file, line = 1, fileFacade = true)
        val found = assertIs<AddressResolution.Found>(resolver.resolve(facade))
        assertEquals(NodeRef.OfArtifact(Graphs.ref(file)), found.node)
    }

    @Test
    fun `addresses that name nothing the graph has are unmapped with a reason`() {
        val reasons = listOf(
            GoldAddress(fqName = "p.Missing", file = file, line = 1) to UnmappedReason.NO_MATCHING_SYMBOL,
            GoldAddress(fqName = "p.Service", file = "other/File.kt", line = 1) to UnmappedReason.NO_SYMBOL_IN_FILE,
            GoldAddress(fqName = "p.Service", line = 1) to UnmappedReason.INCOMPLETE_ADDRESS,
        )
        reasons.forEach { (address, reason) ->
            assertEquals(reason, assertIs<AddressResolution.Unmapped>(resolver.resolve(address)).reason)
        }
    }
}
