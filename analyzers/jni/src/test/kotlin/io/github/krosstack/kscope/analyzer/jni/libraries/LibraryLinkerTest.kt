package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.JniGraph
import io.github.krosstack.kscope.analyzer.jni.Linked
import io.github.krosstack.kscope.analyzer.jni.NativeWorkspace
import io.github.krosstack.kscope.analyzer.jni.loads.LoadCallProducer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CLIENT = "bridge/src/main/kotlin/sample/Native.kt"
private const val CMAKE = "bridge/CMakeLists.txt"
private const val CARGO = "bridge/rust/Cargo.toml"
private const val LOADS_CHECKSUM = "fun f() { System.loadLibrary(\"checksum\") }\n"

private fun cargo(name: String, crateType: String) =
    "[package]\nname = \"x\"\n[lib]\nname = \"$name\"\ncrate-type = [\"$crateType\"]\n"

class LibraryLinkerTest {
    private fun graphOf(files: Map<String, String>): JniGraph {
        val workspace = NativeWorkspace(files)
        val facts = (workspace.produce(LoadCallProducer()) + workspace.produce(LibraryTargetProducer()))
            .flatMap { it.facts }
        return JniGraph(facts, workspace.artifacts)
    }

    private fun link(graph: JniGraph): Linked {
        val linked = mutableListOf<Fact>()
        LibraryLinker().link(graph) { linked += it.facts }
        return Linked(graph, linked)
    }

    private fun link(files: Map<String, String>): Linked = link(graphOf(files))

    private fun bindings(files: Map<String, String>) = link(files).bindings()

    @Test
    fun `a load and a unique shared library target of its name are joined by convention`() {
        val graph = graphOf(mapOf(CLIENT to LOADS_CHECKSUM, CMAKE to "add_library(checksum SHARED a.cpp)\n"))
        val binding = link(graph).relationships.single()
        assertEquals(RelationKind.BINDS, binding.kind)
        assertEquals("native-library", binding.subKind)
        assertEquals(EvidenceClass.CONVENTION, binding.evidenceClass)
        assertEquals(CLIENT, binding.site?.artifact?.path?.value)
        val resolution = binding.resolution as Resolution.Resolved
        assertEquals(Certainty.PROBABLE, resolution.certainty)
        val target = checkNotNull(graph.symbol((resolution.target as NodeRef.OfSymbol).id))
        assertEquals("cmake.library", target.kind.id)
        assertEquals(CMAKE, target.declaration?.artifact?.path?.value)
    }

    @Test
    fun `a cargo cdylib is probable, a library whose type is open or a dylib is heuristic`() {
        mapOf(
            cargo("checksum", "cdylib") to Certainty.PROBABLE,
            cargo("checksum", "dylib") to Certainty.HEURISTIC,
        ).forEach { (manifest, certainty) ->
            val resolution = link(mapOf(CLIENT to LOADS_CHECKSUM, CARGO to manifest))
                .relationships.single().resolution as Resolution.Resolved
            assertEquals(certainty, resolution.certainty, manifest)
        }
        val open = link(mapOf(CLIENT to LOADS_CHECKSUM, CMAKE to "add_library(checksum a.cpp)\n"))
        assertEquals(Certainty.HEURISTIC, (open.relationships.single().resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `several targets of the name leave the load unresolved as ambiguous`() {
        val files = mapOf(
            CLIENT to LOADS_CHECKSUM,
            CMAKE to "add_library(checksum SHARED a.cpp)\n",
            CARGO to cargo("checksum", "cdylib"),
        )
        assertEquals(listOf("checksum -> unresolved artifact.ambiguous-producer"), bindings(files))
    }

    @Test
    fun `no target of the name in a workspace with native build files is a producer that is not in the workspace`() {
        val builds = listOf(
            "add_library(other SHARED a.cpp)\nadd_library(checksum STATIC a.cpp)\n",
            "project(x)\n",
        )
        builds.forEach { build ->
            assertEquals(
                listOf("checksum -> unresolved artifact.producer-not-in-workspace"),
                bindings(mapOf(CLIENT to LOADS_CHECKSUM, CMAKE to build)),
                build,
            )
        }
    }

    @Test
    fun `a workspace without native build files says that the native build was not analysed`() {
        val files = mapOf(CLIENT to LOADS_CHECKSUM, "bridge/src/main.c" to "int main(void) { return 0; }\n")
        assertEquals(listOf("checksum -> not-analysed jni.native-build-not-analysed"), bindings(files))
    }

    @Test
    fun `without a load the linker says nothing`() {
        val files = mapOf(CMAKE to "add_library(checksum SHARED a.cpp)\n")
        assertEquals(emptyList(), link(files).facts)
    }

    @Test
    fun `every load of the workspace gets exactly one answer, in a fixed order`() {
        val files = mapOf(
            CLIENT to "fun f() { System.loadLibrary(\"b\"); System.loadLibrary(\"a\"); System.loadLibrary(\"c\") }\n",
            CMAKE to "add_library(a SHARED a.cpp)\nadd_library(b SHARED b.cpp)\n",
        )
        val linked = link(files)
        assertEquals(3, linked.relationships.size)
        assertEquals(linked.facts, link(files).facts)
        assertEquals(
            listOf("a -> a PROBABLE", "b -> b PROBABLE", "c -> unresolved artifact.producer-not-in-workspace"),
            linked.bindings(),
        )
    }
}
