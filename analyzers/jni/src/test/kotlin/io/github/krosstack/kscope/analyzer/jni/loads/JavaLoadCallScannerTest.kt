package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaLoadCallScannerTest {
    private fun java(text: String) = LoadCallScanner(text.trimIndent(), JvmDialect.JAVA).scan()

    @Test
    fun `a dollar sign is a character in java and no template`() {
        assertEquals(listOf("x\$y"), java("System.loadLibrary(\"x\$y\");").map { it.library })
    }

    @Test
    fun `java has no trailing comma in a call`() {
        assertEquals(emptyList(), java("System.loadLibrary(\"a\",);"))
    }

    @Test
    fun `calls are read with java rules`() {
        val calls = java(
            """
            class Native {
                static {
                    System.loadLibrary("jlib"); /* System.loadLibrary("no") */
                    String t = ${'"'}${'"'}${'"'}
                        System.loadLibrary("text")
                        ${'"'}${'"'}${'"'};
                }
            }
            """,
        )
        assertEquals(listOf("jlib"), calls.map { it.library })
        assertEquals(TextRange(3, 9, 3, 35), calls.single().declaration)
    }

    @Test
    fun `a library that java loads by native load is named after the file`() {
        val calls = java("Native.load(\"c\", Libc.class); System.load(\"/usr/lib/libz.so\");")
        assertEquals(listOf("c", "z"), calls.map { it.library })
    }
}
