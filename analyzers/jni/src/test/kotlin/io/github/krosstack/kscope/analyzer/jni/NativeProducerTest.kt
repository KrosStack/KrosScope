package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.ArtifactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val CRC32 = "Java_sample_tasktide_nativebridge_Checksum_crc32"
private const val VERSION = "Java_sample_tasktide_nativebridge_Checksum_nativeVersion"

class NativeProducerTest {
    private val workspace = NativeWorkspace(
        mapOf(
            CHECKSUM_PATH to CHECKSUM_C,
            "bridge/src/main/cpp/checksum.h" to "JNIEXPORT jint JNICALL $CRC32(JNIEnv *, jobject, jbyteArray);\n",
        ),
    )

    private fun detect(path: String): String? {
        val candidate = ArtifactCandidate(RepoPath(path), 0) { ByteArray(0) }
        return NativeSourceDetector().detect(candidate)?.id
    }

    @Test
    fun `c and c plus plus sources and headers are native source`() {
        listOf("a.c", "a.cc", "a.cpp", "a.cxx", "a.c++", "a.h", "a.hh", "a.hpp", "a.hxx", "src/main/cpp/B.CPP")
            .forEach { assertEquals("native.source", detect(it), it) }
    }

    @Test
    fun `other files are not native source`() {
        listOf("a.kt", "a.java", "CMakeLists.txt", "a.cmake", "c", "a.cpp.orig", "a.m")
            .forEach { assertNull(detect(it), it) }
    }

    @Test
    fun `a definition and a prototype are separate contract symbols with their own identities`() {
        val lines = workspace.symbols().map {
            val identity = (it.id.identity() as ParsedSymbolId.Valid).identity
            "${it.attributes["jni.role"]} ${it.name} ${identity.unit.substringAfter("//")} ${identity.sourceSet} " +
                identity.descriptor.render()
        }
        assertEquals(
            listOf(
                "definition $CRC32 :bridge main `bridge/src/main/cpp/checksum.c`:definition:$CRC32.",
                "definition $VERSION :bridge main `bridge/src/main/cpp/checksum.c`:definition:$VERSION.",
                "declaration $CRC32 :bridge main `bridge/src/main/cpp/checksum.h`:declaration:$CRC32.",
            ),
            lines,
        )
    }

    @Test
    fun `a native function is a textual contract symbol with its place in the file`() {
        val crc32 = workspace.symbols().single {
            it.name.endsWith("crc32") && it.declaration?.artifact?.path?.value == CHECKSUM_PATH
        }
        assertEquals("jni.native-function", crc32.kind.id)
        assertEquals(SymbolCategory.CONTRACT, crc32.kind.category)
        assertEquals(EvidenceClass.TEXTUAL, crc32.evidenceClass)
        assertEquals(Certainty.EXACT, crc32.certainty)
        assertEquals(SymbolOrigin.Source, crc32.origin)
        assertEquals(crc32.name, crc32.qualifiedName)
        assertEquals(9, crc32.declaration?.range?.startLine)
        assertEquals(10, crc32.nameLocation?.range?.startLine)
        assertEquals(NativeModel.sourceSet, crc32.scope.sourceSet)
    }

    @Test
    fun `producing twice gives the same facts`() {
        assertEquals(workspace.produce(), workspace.produce())
    }

    @Test
    fun `a file outside every module only gets a note`() {
        val facts = workspace.produce(inModule = false).flatMap { it.facts }
        assertEquals(setOf("jni.module-unknown"), facts.map { (it as Fact.OfDiagnostic).value.code.value }.toSet())
        assertEquals(DiagnosticSeverity.INFO, (facts.first() as Fact.OfDiagnostic).value.severity)
    }

    @Test
    fun `a function declared twice in one file is one symbol per role`() {
        val twice = NativeWorkspace(
            mapOf(CHECKSUM_PATH to "void Java_p_C_f(void);\nvoid Java_p_C_f(void);\nvoid Java_p_C_f(void) {}\n"),
        )
        assertEquals(listOf("declaration", "definition"), twice.symbols().map { it.attributes["jni.role"] })
    }
}
