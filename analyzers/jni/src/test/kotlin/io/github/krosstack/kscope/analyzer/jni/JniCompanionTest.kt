package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.JniScenario.external
import io.github.krosstack.kscope.analyzer.jni.JniScenario.link
import io.github.krosstack.kscope.model.SymbolFlag
import kotlin.test.Test
import kotlin.test.assertEquals

class JniCompanionTest {
    private val holder = Declarations.type("p", listOf("Holder", "Companion"), "kotlin.object", "companion object")
    private val plain = ExternalSpec("p", listOf("Holder", "Companion"), "run", "external fun run()")
    private val static = plain.copy(
        method = "go",
        signature = "@JvmStatic external fun go()",
        flags = setOf(SymbolFlag.EXTERNAL, SymbolFlag.STATIC),
    )

    private fun definition(vararg names: String) =
        mapOf(CHECKSUM_PATH to names.joinToString("\n") { "void $it(void) {}" })

    @Test
    fun `a member of a companion is in the companion class`() {
        val linked = link(listOf(holder, external(plain, holder)), definition("Java_p_Holder_00024Companion_run"))
        assertEquals(
            listOf("Java_p_Holder_00024Companion_run -> Java_p_Holder_00024Companion_run EXACT"),
            linked.bindings(),
        )
    }

    @Test
    fun `a jvm static member of a companion is in the outer class`() {
        val linked = link(listOf(holder, external(static, holder)), definition("Java_p_Holder_go"))
        assertEquals(listOf("Java_p_Holder_go -> Java_p_Holder_go EXACT"), linked.bindings())
    }

    @Test
    fun `a jvm static member found in the companion class is only probable`() {
        val linked = link(listOf(holder, external(static, holder)), definition("Java_p_Holder_00024Companion_go"))
        assertEquals(listOf("Java_p_Holder_go -> Java_p_Holder_00024Companion_go PROBABLE"), linked.bindings())
    }
}
