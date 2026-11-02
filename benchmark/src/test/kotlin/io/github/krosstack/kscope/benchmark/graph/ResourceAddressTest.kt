package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResourceAddressTest {
    private fun nodeOf(resolver: GoldAddressResolver, address: GoldAddress) =
        assertIs<AddressResolution.Found>(resolver.resolve(address)).node

    private val strings = "app/src/main/res/values/strings.xml"

    private fun resource(type: String, line: Int, id: String) =
        Graphs.symbol(strings, "app.R.$type.title", "android.resource.$type", line, SymbolDescriptor().term(id))

    private fun address(type: String, line: Int, name: String = "title") =
        GoldAddress(file = strings, kind = "android.resource.$type", name = name, line = line)

    @Test
    fun `a resource declaration is found by its file, kind, name and line`() {
        val first = resource("string", 3, "a")
        val second = resource("string", 7, "b")
        val dimen = resource("dimen", 4, "c")
        val resolver = GoldAddressResolver(FakeGraph(symbols = listOf(first, second, dimen)))
        assertEquals(NodeRef.OfSymbol(second.id), nodeOf(resolver, address("string", 7)))
        assertEquals(NodeRef.OfSymbol(dimen.id), nodeOf(resolver, address("dimen", 4)))
    }

    @Test
    fun `a resource declaration that is ambiguous or missing is unmapped`() {
        val resolver = GoldAddressResolver(
            FakeGraph(symbols = listOf(resource("string", 3, "a"), resource("string", 7, "b"))),
        )
        listOf(address("string", 9), address("string", 3, name = "gone")).forEach {
            val unmapped = assertIs<AddressResolution.Unmapped>(resolver.resolve(it))
            assertEquals(UnmappedReason.NO_MATCHING_SYMBOL, unmapped.reason)
        }
    }
}
