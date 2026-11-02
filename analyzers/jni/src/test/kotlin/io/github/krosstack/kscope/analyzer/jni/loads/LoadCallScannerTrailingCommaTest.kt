package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadCallScannerTrailingCommaTest {
    private fun kotlin(text: String) = LoadCallScanner(text.trimIndent(), JvmDialect.KOTLIN).scan()

    private fun libraries(text: String) = kotlin(text).map { it.library }

    @Test
    fun `a trailing comma after the last argument is still the call`() {
        val text = """
            System.loadLibrary("a",)
            System.load("/usr/lib/libb.so" , )
            Native.load("c", Lib::class.java,)
            Native.loadLibrary("d", Lib.class,)
            Native.load("e",)
            System.loadLibrary(
                "f",
            )
        """
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), libraries(text))
    }

    @Test
    fun `the span of a call with a trailing comma ends at the closing parenthesis`() {
        val call = kotlin("System.loadLibrary(\n    \"multi\",\n)").single()
        assertEquals(TextRange(1, 1, 3, 2), call.declaration)
        assertEquals(TextRange(2, 5, 2, 12), call.argumentRange)
    }

    @Test
    fun `a second argument after the name is not a load`() {
        val text = """
            System.loadLibrary("a", other)
            System.loadLibrary("b", other,)
            System.load("/x/libc.so", other)
            System.loadLibrary("kept")
        """
        assertEquals(listOf("kept"), libraries(text))
    }
}
