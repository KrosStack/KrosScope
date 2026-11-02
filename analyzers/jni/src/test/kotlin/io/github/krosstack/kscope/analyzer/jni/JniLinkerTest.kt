package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.JniScenario.external
import io.github.krosstack.kscope.analyzer.jni.JniScenario.link
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JniLinkerTest {
    @Test
    fun `an external function with a definition is bound exactly and generates its contract`() {
        val linked = link(listOf(external(crc32), external(nativeVersion)))
        assertEquals(
            listOf(
                "${shortName("crc32")} -> ${shortName("crc32")} EXACT",
                "${shortName("nativeVersion")} -> ${shortName("nativeVersion")} EXACT",
            ),
            linked.bindings(),
        )
        val binding = linked.relationships.first { it.kind == RelationKind.BINDS }
        assertEquals(EvidenceClass.CONVENTION, binding.evidenceClass)
        assertEquals(crc32.path, binding.site?.artifact?.path?.value)
    }

    @Test
    fun `the contract symbol carries the short and the long name and is generated from the declaration`() {
        val declaration = external(crc32)
        val linked = link(listOf(declaration))
        val contract = linked.symbols.single()
        assertEquals("jni.function", contract.kind.id)
        assertEquals(shortName("crc32"), contract.name)
        assertEquals(
            mapOf("jni.long-name" to "${shortName("crc32")}___3B", "jni.short-name" to shortName("crc32")),
            contract.attributes,
        )
        assertEquals(SymbolOrigin.Generated("jni", NodeRef.OfSymbol(declaration.id)), contract.origin)
        val generates = linked.relationships.single { it.kind == RelationKind.GENERATES }
        assertEquals(NodeRef.OfSymbol(declaration.id), generates.from)
        assertEquals(Resolution.Resolved(NodeRef.OfSymbol(contract.id), Certainty.EXACT), generates.resolution)
        assertEquals(EvidenceClass.CONVENTION, generates.evidenceClass)
        assertTrue(contract.id.value.contains("jni/sample/tasktide/nativebridge/Checksum#crc32(ByteArray)."))
    }

    @Test
    fun `a prototype alone makes the binding probable`() {
        val prototype = "JNIEXPORT jint JNICALL ${shortName("crc32")}(JNIEnv *, jobject, jbyteArray);\n"
        val header = mapOf("bridge/src/main/cpp/checksum.h" to prototype)
        assertEquals(
            listOf("${shortName("crc32")} -> ${shortName("crc32")} PROBABLE"),
            link(listOf(external(crc32)), header).bindings(),
        )
    }

    @Test
    fun `a definition wins over the prototype of the same function`() {
        val both = mapOf(
            CHECKSUM_PATH to CHECKSUM_C,
            "bridge/src/main/cpp/checksum.h" to "void ${shortName("crc32")}(void);\n",
        )
        val bindings = link(listOf(external(crc32)), both).relationships.filter { it.kind == RelationKind.BINDS }
        assertEquals(1, bindings.size)
        assertEquals(Certainty.EXACT, (bindings.single().resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `native sources without a function of that name leave the binding unresolved`() {
        val other = mapOf(CHECKSUM_PATH to "void Java_other_Thing_run(JNIEnv *env, jobject self) {}\n")
        assertEquals(
            listOf("${shortName("crc32")} -> unresolved jni.definition-not-found"),
            link(listOf(external(crc32)), other).bindings(),
        )
    }

    @Test
    fun `a Rust definition binds like a C one`() {
        val rust = mapOf(
            "bridge/src/main/rust/lib.rs" to
                "#[no_mangle]\npub extern \"system\" fn ${shortName("crc32")}(env: JNIEnv) -> jint { 0 }\n",
        )
        assertEquals(
            listOf("${shortName("crc32")} -> ${shortName("crc32")} EXACT"),
            link(listOf(external(crc32)), rust).bindings(),
        )
    }

    @Test
    fun `a workspace with Rust sources only has looked at the native side`() {
        val rust = mapOf("bridge/src/main/rust/lib.rs" to "pub extern \"C\" fn Java_other_Thing_run() {}\n")
        assertEquals(
            listOf("${shortName("crc32")} -> unresolved jni.definition-not-found"),
            link(listOf(external(crc32)), rust).bindings(),
        )
    }

    @Test
    fun `a workspace without native sources says the native side was not analysed`() {
        assertEquals(
            listOf("${shortName("crc32")} -> not-analysed jni.native-side-not-analysed"),
            link(listOf(external(crc32)), emptyMap()).bindings(),
        )
    }

    @Test
    fun `several definitions of one function make each binding probable`() {
        val twice = mapOf(CHECKSUM_PATH to CHECKSUM_C, "bridge/src/main/cpp/other.c" to CHECKSUM_C)
        val relationships = link(listOf(external(nativeVersion)), twice).relationships
        val bindings = relationships.filter { it.kind == RelationKind.BINDS }
        val certainties = bindings.map { (it.resolution as Resolution.Resolved).certainty }
        assertEquals(listOf(Certainty.PROBABLE, Certainty.PROBABLE), certainties)
    }

    @Test
    fun `the result does not depend on the order of the symbols`() {
        val externals = listOf(external(crc32), external(nativeVersion))
        assertEquals(link(externals).facts, link(externals.reversed()).facts)
    }
}
