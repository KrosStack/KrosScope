package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadCallScannerTest {
    private fun kotlin(text: String) = LoadCallScanner(text.trimIndent(), JvmDialect.KOTLIN).scan()

    private fun libraries(text: String) = kotlin(text).map { it.library }

    @Test
    fun `the load of the reference client names its library and says where the call and the argument are`() {
        val call = kotlin(
            """
            object NativeChecksum {
                init {
                    System.loadLibrary("tasktide_checksum")
                }
            }
            """,
        ).single()
        assertEquals("tasktide_checksum", call.library)
        assertEquals("tasktide_checksum", call.argument)
        assertEquals("System.loadLibrary", call.call)
        assertEquals(TextRange(3, 9, 3, 48), call.declaration)
        assertEquals(TextRange(3, 28, 3, 47), call.argumentRange)
    }

    @Test
    fun `every form with a string literal is read`() {
        val text = """
            System.loadLibrary("a")
            System.load("/data/app/lib/libb.so")
            Native.load("c", Lib::class.java)
            Native.loadLibrary("d", Lib.class)
            Native.load("e")
            java.lang.System . loadLibrary ( "f" )
        """
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), libraries(text))
        assertEquals(
            listOf("System.loadLibrary", "System.load", "Native.load", "Native.loadLibrary", "Native.load"),
            kotlin(text).map { it.call }.take(5),
        )
    }

    @Test
    fun `the library of a path is its file name without prefix and extension`() {
        val text = """
            System.load("/usr/lib/libfoo.so")
            System.load("C:\\Program Files\\app\\bar.dll")
            System.load("lib/libbaz.dylib")
            System.load("plain")
            System.load("/opt/libqux.so.1")
        """
        assertEquals(listOf("foo", "bar", "baz", "plain", "qux"), libraries(text))
    }

    @Test
    fun `a name that is built or not a string is not a library`() {
        val text = """
            System.loadLibrary(name)
            System.loadLibrary("lib" + suffix)
            System.loadLibrary("x${'$'}suffix")
            System.loadLibrary("x${'$'}{suffix}")
            System.loadLibrary()
            Native.load(Lib::class.java)
            Native.loadLibrary("y" + z, Lib::class.java)
            System.loadLibrary("")
            System.loadLibrary('c')
            other.System.exit("z")
            System.loadLibrary("kept")
        """
        assertEquals(listOf("kept"), libraries(text))
    }

    @Test
    fun `comments and strings hide a call`() {
        val text = """
            // System.loadLibrary("line")
            /* System.loadLibrary("block") /* System.loadLibrary("nested") */ */
            val a = "System.loadLibrary(\"string\")"
            val b = ${'"'}${'"'}${'"'}System.loadLibrary("raw")${'"'}${'"'}${'"'}
            val c = "${'$'}{System.loadLibrary("template")}"
            System.loadLibrary("real")
        """
        assertEquals(listOf("real"), libraries(text))
    }

    @Test
    fun `comments between the parts of a call do not hide it`() {
        val text = """
            System /* native */ .loadLibrary("a")
            System. /* native */ loadLibrary("b")
            System // native
                .loadLibrary("c")
        """
        assertEquals(listOf("a", "b", "c"), libraries(text))
    }

    @Test
    fun `a call over several lines spans them`() {
        val call = kotlin("System.loadLibrary(\n    \"multi\"\n)").single()
        assertEquals("multi", call.library)
        assertEquals(TextRange(1, 1, 3, 2), call.declaration)
        assertEquals(TextRange(2, 5, 2, 12), call.argumentRange)
    }

    @Test
    fun `a text without a load gives nothing`() {
        assertEquals(emptyList(), kotlin("fun main() { println(\"hello\") }"))
    }
}
