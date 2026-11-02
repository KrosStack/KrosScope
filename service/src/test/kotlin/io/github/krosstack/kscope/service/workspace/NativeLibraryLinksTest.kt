package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CLIENT = "tasktide-app/client/src/main/kotlin/sample/tasktide/client"

/** A native method of the client, the function that implements it and the binding between them. */
private class NativeBinding(val declaration: NodeRef, val function: NodeRef, val relationship: Relationship)

class NativeLibraryLinksTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `System loadLibrary in the client is linked to the library target that builds it in another repository`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val query = RelationshipQuery(kinds = setOf(RelationKind.BINDS), subKind = "native-library")
                val links = graph.relationships(query).associateBy { graph.placeOf(it.from) }

                assertEquals(setOf("$CLIENT/NativeChecksum.kt:5", "$CLIENT/NativeHash.kt:5"), links.keys)
                val checksum = links.getValue("$CLIENT/NativeChecksum.kt:5")
                val hash = links.getValue("$CLIENT/NativeHash.kt:5")
                assertEquals("tasktide-native-cpp/CMakeLists.txt:6", graph.placeOf(checksum.target()))
                assertEquals("tasktide-native-rust/Cargo.toml:7", graph.placeOf(hash.target()))
                assertEquals("cmake.library", graph.symbolOf(checksum.target()).kind.id)
                assertEquals("cargo.library", graph.symbolOf(hash.target()).kind.id)
                assertEquals("jvm.load-library", graph.symbolOf(checksum.from).kind.id)
                assertEquals("tasktide_checksum", graph.symbolOf(checksum.from).name)
                assertEquals("tasktide_hash", graph.symbolOf(hash.from).name)
                links.values.forEach {
                    assertEquals(EvidenceClass.CONVENTION, it.evidenceClass)
                    assertEquals(Certainty.PROBABLE, (it.resolution as Resolution.Resolved).certainty)
                }
            }
        }

    @Test
    fun `external functions of the client are bound to the JNI functions of the C plus plus and Rust repositories`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val crc32 = graph.nativeBinding("crc32")
                val fnv1a = graph.nativeBinding("fnv1a")

                assertEquals("$CLIENT/NativeChecksum.kt:8", graph.placeOf(crc32.declaration))
                assertEquals("$CLIENT/NativeHash.kt:8", graph.placeOf(fnv1a.declaration))
                assertEquals("tasktide-native-cpp/src/checksum.cpp:22", graph.placeOf(crc32.function))
                assertEquals("tasktide-native-rust/src/lib.rs:15", graph.placeOf(fnv1a.function))
                assertEquals("Java_sample_tasktide_client_NativeChecksum_crc32", graph.symbolOf(crc32.function).name)
                assertEquals("Java_sample_tasktide_client_NativeHash_fnv1a", graph.symbolOf(fnv1a.function).name)
                listOf(crc32, fnv1a).forEach {
                    assertEquals(EvidenceClass.CONVENTION, it.relationship.evidenceClass)
                    assertEquals(Certainty.EXACT, (it.relationship.resolution as Resolution.Resolved).certainty)
                }
            }
        }

    @Test
    fun `a repository without a build holds its files in a module of its own`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                val scan = service.scan()
                val graph = service.graph()
                val cpp = graph.structure().repositories.single { it.directory.value == "tasktide-native-cpp" }
                val modules = graph.structure().modules.filter { it.id.value.startsWith(cpp.id.value) }
                val files = graph.artifacts().filter { it.ref.repository == cpp.id }

                assertEquals(listOf(":"), modules.map { it.path })
                assertEquals("true", modules.single().attributes["kscope.repository-module"])
                assertTrue(files.isNotEmpty() && files.all { it.sourceSet != null }, "$files")
                assertTrue(scan.diagnostics.entries.any { it.code == "build-model.repository-module" })
                assertTrue(scan.diagnostics.entries.none { it.code == "build-model.not-available" })
            }
        }

    private fun GraphReader.nativeBinding(name: String): NativeBinding {
        val declaration = symbols(SymbolQuery(name = name, flag = SymbolFlag.EXTERNAL, limit = Int.MAX_VALUE)).single()
        val generates = outgoing(NodeRef.OfSymbol(declaration.id), setOf(RelationKind.GENERATES)).single()
        val binding = outgoing(generates.target(), setOf(RelationKind.BINDS)).single()
        return NativeBinding(NodeRef.OfSymbol(declaration.id), binding.target(), binding)
    }

    private fun Relationship.target(): NodeRef = (resolution as Resolution.Resolved).target

    private fun GraphReader.symbolOf(node: NodeRef): Symbol = checkNotNull(symbol((node as NodeRef.OfSymbol).id))

    private fun GraphReader.placeOf(node: NodeRef): String {
        val location = checkNotNull(symbolOf(node).nameLocation)
        val directory = structure().repositories.single { it.id == location.artifact.repository }.directory.value
        return "$directory/${location.artifact.path.value}:${location.range.startLine}"
    }
}
