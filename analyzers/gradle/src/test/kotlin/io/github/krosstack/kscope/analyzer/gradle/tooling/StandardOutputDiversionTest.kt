package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StandardOutputDiversionTest {
    private val originalOut = System.out
    private val originalErr = System.err
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    init {
        System.setOut(PrintStream(out, true))
        System.setErr(PrintStream(err, true))
    }

    @AfterTest
    fun restore() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun `what the action prints on standard output goes to standard error and the result is returned`() {
        val result = StandardOutputDiversion.toStandardError {
            print("Downloading https://example.invalid/gradle-bin.zip")
            42
        }
        assertEquals(42, result)
        assertEquals("", out.toString())
        assertEquals("Downloading https://example.invalid/gradle-bin.zip", err.toString())
    }

    @Test
    fun `standard output is restored afterwards, also when the action fails`() {
        val before = System.out
        assertFailsWith<IllegalStateException> { StandardOutputDiversion.toStandardError { error("boom") } }
        assertSame(before, System.out)
        print("result")
        assertEquals("result", out.toString())
    }
}
