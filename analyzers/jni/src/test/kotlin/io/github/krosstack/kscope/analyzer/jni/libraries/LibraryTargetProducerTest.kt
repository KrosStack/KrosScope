package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.NativeModel
import io.github.krosstack.kscope.analyzer.jni.NativeWorkspace
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CMAKE = "bridge/CMakeLists.txt"
private const val CARGO = "bridge/Cargo.toml"
private const val NDK = "bridge/jni/Android.mk"

class LibraryTargetProducerTest {
    private val workspace = NativeWorkspace(
        mapOf(
            CMAKE to "project(x)\nadd_library(tasktide_checksum SHARED a.cpp)\nadd_library(core STATIC c.c)\n",
            CARGO to "[lib]\nname = \"tasktide_hash\"\ncrate-type = [\"cdylib\"]\n",
            NDK to "include \$(CLEAR_VARS)\nLOCAL_MODULE := legacy\ninclude \$(BUILD_SHARED_LIBRARY)\n",
            "bridge/src/main.c" to "int main(void) { return 0; }\n",
        ),
    )

    private fun symbols() = workspace.symbols(LibraryTargetProducer())

    @Test
    fun `each build file gives the symbols of its shared libraries only`() {
        assertEquals(
            listOf(
                "cargo.library tasktide_hash",
                "cmake.library tasktide_checksum",
                "ndk.library legacy",
            ),
            symbols().map { "${it.kind.id} ${it.name}" }.sorted(),
        )
    }

    @Test
    fun `a library target is a textual build symbol with its place in the file`() {
        val target = symbols().single { it.kind.id == "cmake.library" }
        assertEquals(SymbolCategory.BUILD, target.kind.category)
        assertEquals(EvidenceClass.TEXTUAL, target.evidenceClass)
        assertEquals(Certainty.EXACT, target.certainty)
        assertEquals(SymbolOrigin.Source, target.origin)
        assertEquals(mapOf("library.linkage" to "shared"), target.attributes)
        assertEquals("add_library(tasktide_checksum SHARED)", target.signature)
        assertEquals(CMAKE, target.declaration?.artifact?.path?.value)
        assertEquals(2, target.nameLocation?.range?.startLine)
        assertEquals(NativeModel.sourceSet, target.scope.sourceSet)
    }

    @Test
    fun `the identity names the module, the file and the library`() {
        val target = symbols().single { it.kind.id == "cargo.library" }
        val identity = (target.id.identity() as ParsedSymbolId.Valid).identity
        assertEquals(":bridge", identity.unit.substringAfter("//"))
        assertEquals("`$CARGO`:tasktide_hash.", identity.descriptor.render())
    }

    @Test
    fun `producing twice gives the same facts`() {
        assertEquals(workspace.produce(LibraryTargetProducer()), workspace.produce(LibraryTargetProducer()))
    }

    @Test
    fun `a file outside every module that declares a library only gets a note`() {
        val facts = workspace.produce(LibraryTargetProducer(), inModule = false).flatMap { it.facts }
        assertEquals(List(3) { "jni.module-unknown" }, facts.map { (it as Fact.OfDiagnostic).value.code.value })
    }

    @Test
    fun `a build file that declares no library gives no fact`() {
        val none = NativeWorkspace(mapOf(CMAKE to "project(x)\nadd_executable(x main.c)\n"))
        assertEquals(emptyList(), none.produce(LibraryTargetProducer(), inModule = false).flatMap { it.facts })
    }
}
