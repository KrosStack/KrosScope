package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.JniScenario.external
import io.github.krosstack.kscope.analyzer.jni.JniScenario.link
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

class JniNamingTest {
    private fun definition(vararg names: String) =
        mapOf(CHECKSUM_PATH to names.joinToString("\n") { "void $it(void) {}" })

    @Test
    fun `overloaded functions are told apart by the long name`() {
        val ints = crc32.copy(method = "mix", signature = "external fun mix(a: Int, b: String)")
            .copy(parameters = "Int,String")
        val longs = ints.copy(signature = "external fun mix(a: Long)", parameters = "Long")
        val c = definition("${shortName("mix")}__ILjava_lang_String_2")
        val linked = link(listOf(external(ints), external(longs)), c)
        assertEquals(
            listOf(
                "${shortName("mix")} -> ${shortName("mix")}__ILjava_lang_String_2 EXACT",
                "${shortName("mix")} -> unresolved jni.definition-not-found",
            ),
            linked.bindings(),
        )
    }

    @Test
    fun `the short name of an overloaded function is only probable`() {
        val first = crc32.copy(method = "mix", signature = "external fun mix(a: Int)", parameters = "Int")
        val second = first.copy(signature = "external fun mix(a: Long)", parameters = "Long")
        val bindings = link(listOf(external(first), external(second)), definition(shortName("mix"))).bindings()
        assertEquals(2, bindings.size)
        assertEquals(listOf(true, true), bindings.map { it.endsWith("PROBABLE") })
    }

    @Test
    fun `a java native method is bound like a kotlin external function`() {
        val java = ExternalSpec(
            packageName = "p",
            types = listOf("Maths"),
            method = "add",
            signature = "public native int add(int a, int b)",
            kind = "java.method",
            flags = setOf(SymbolFlag.EXTERNAL),
            parameters = "int,int",
            path = "bridge/src/main/java/p/Maths.java",
        )
        val linked = link(listOf(external(java)), definition("Java_p_Maths_add"))
        assertEquals(listOf("Java_p_Maths_add -> Java_p_Maths_add EXACT"), linked.bindings())
        assertEquals("Java_p_Maths_add__II", linked.symbols.single().attributes["jni.long-name"])
    }

    @Test
    fun `a top level function lives in the facade of its file`() {
        val top = ExternalSpec(
            packageName = "p",
            types = emptyList(),
            method = "hash",
            signature = "external fun hash(x: Long): Long",
            parameters = "Long",
            path = "bridge/src/main/kotlin/p/NativeOps.kt",
        )
        val linked = link(listOf(external(top)), definition("Java_p_NativeOpsKt_hash"))
        assertEquals(listOf("Java_p_NativeOpsKt_hash -> Java_p_NativeOpsKt_hash EXACT"), linked.bindings())
    }

    @Test
    fun `a nested class is separated by an escaped dollar sign`() {
        val nested = crc32.copy(types = listOf("Outer", "Inner"), method = "run", signature = "external fun run()")
        val name = "Java_sample_tasktide_nativebridge_Outer_00024Inner_run"
        val linked = link(listOf(external(nested)), definition(name))
        assertEquals(1, linked.bindings().count { it.endsWith("EXACT") })
    }

    @Test
    fun `a parameter type of the workspace is found by its simple name for the long name`() {
        val task = Declarations.type("sample.core", listOf("Task"), "kotlin.class")
        val spec = crc32.copy(method = "save", signature = "external fun save(task: Task)", parameters = "Task")
        val linked = link(listOf(task, external(spec)), definition(shortName("save")))
        assertEquals("${shortName("save")}__Lsample_core_Task_2", linked.symbols.single().attributes["jni.long-name"])
    }

    @Test
    fun `a parameter type that nothing explains leaves out the long name`() {
        val spec = crc32.copy(method = "save", signature = "external fun save(task: Unknown)", parameters = "Unknown")
        val linked = link(listOf(external(spec)), definition(shortName("save")))
        assertEquals(mapOf("jni.short-name" to shortName("save")), linked.symbols.single().attributes)
    }

    @Test
    fun `declarations that are not native methods of the jvm are left alone`() {
        val library = Declarations.external(crc32, origin = SymbolOrigin.Library("x:y"))
        val property = external(nativeVersion.copy(kind = "kotlin.property"))
        val plain = external(nativeVersion.copy(method = "plain", flags = emptySet()))
        assertEquals(emptyList(), link(listOf(library, property, plain)).facts)
    }
}
