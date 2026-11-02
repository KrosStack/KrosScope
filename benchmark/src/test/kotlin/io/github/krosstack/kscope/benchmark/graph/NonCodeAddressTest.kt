package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NonCodeAddressTest {
    private fun nodeOf(resolver: GoldAddressResolver, address: GoldAddress) =
        assertIs<AddressResolution.Found>(resolver.resolve(address)).node

    @Test
    fun `modules are found by their path and those of an included build below the directory of that build`() {
        val main = Graphs.buildRoot("")
        val logic = Graphs.buildRoot("build-logic")
        val core = Graphs.module(":core", "core", main.id)
        val logicModule = Graphs.module(":", "build-logic", logic.id, setOf(ModuleTrait.BUILD_LOGIC))
        val graph = FakeGraph(modules = listOf(core, logicModule), buildRoots = listOf(main, logic))
        val resolver = GoldAddressResolver(graph)
        val coreAddress = GoldAddress(module = ":core", kind = "gradle.module", name = ":core")
        val logicAddress = GoldAddress(module = ":build-logic", kind = "gradle.module", name = ":build-logic")
        assertEquals(NodeRef.OfModule(core.id), nodeOf(resolver, coreAddress))
        assertEquals(NodeRef.OfModule(logicModule.id), nodeOf(resolver, logicAddress))
    }

    @Test
    fun `a module that the build does not have is unmapped`() {
        val resolver = GoldAddressResolver(FakeGraph())
        val address = GoldAddress(module = ":gone", kind = "gradle.module", name = ":gone")
        val unmapped = assertIs<AddressResolution.Unmapped>(resolver.resolve(address))
        assertEquals(UnmappedReason.MODULE_NOT_FOUND, unmapped.reason)
    }

    @Test
    fun `a catalog entry is found by its file and name`() {
        val entry = Graphs.symbol("gradle/libs.versions.toml", "libs.junit", "gradle.catalog.library", 5)
            .copy(name = "junit")
        val resolver = GoldAddressResolver(FakeGraph(symbols = listOf(entry)))
        val address = GoldAddress(file = "gradle/libs.versions.toml", kind = "catalog.library", name = "junit")
        assertEquals(NodeRef.OfSymbol(entry.id), nodeOf(resolver, address))
    }

    @Test
    fun `a convention plugin is its script`() {
        val script = "build-logic/src/main/kotlin/app.library.gradle.kts"
        val resolver = GoldAddressResolver(FakeGraph(artifacts = listOf(Graphs.artifact(script, "kotlin.script"))))
        val address = GoldAddress(kind = "gradle.convention-plugin", name = "app.library", file = script)
        assertEquals(NodeRef.OfArtifact(Graphs.ref(script)), nodeOf(resolver, address))
    }

    @Test
    fun `a generated surface that no producer made is unmapped with that reason`() {
        val address = GoldAddress(fqName = "p.ServiceQueries", kind = "generated.sqldelight-query", file = "x.sq")
        val unmapped = assertIs<AddressResolution.Unmapped>(GoldAddressResolver(FakeGraph()).resolve(address))
        assertEquals(UnmappedReason.GENERATED_SURFACE_NOT_PRODUCED, unmapped.reason)
    }

    @Test
    fun `a generated surface is found once a producer has made it`() {
        val symbol = Graphs.symbol("build/Queries.kt", "p.ServiceQueries", "kotlin.class", 1)
        val made = symbol.copy(origin = SymbolOrigin.Generated("sqldelight", null))
        val address = GoldAddress(fqName = "p.ServiceQueries", kind = "generated.sqldelight-query", file = "x.sq")
        val resolver = GoldAddressResolver(FakeGraph(symbols = listOf(made)))
        assertEquals(NodeRef.OfSymbol(made.id), nodeOf(resolver, address))
    }

    @Test
    fun `a kind that no producer knows yet is unmapped with that reason`() {
        val address = GoldAddress(kind = "cmake.target", name = "native", file = "x.txt")
        val unmapped = assertIs<AddressResolution.Unmapped>(GoldAddressResolver(FakeGraph()).resolve(address))
        assertEquals(UnmappedReason.KIND_NOT_SUPPORTED, unmapped.reason)
    }
}
