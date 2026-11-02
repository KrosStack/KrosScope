package io.github.krosstack.kscope.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessStreamsTest {
    private fun properties(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun `redirected streams are UTF-8 whatever the platform charset is`() {
        val streams = ProcessStreams.detect(
            stdoutIsConsole = false,
            properties = properties("sun.stdout.encoding" to "Cp850"),
        )

        assertEquals(Charsets.UTF_8, streams.stdoutCharset)
        assertEquals(Charsets.UTF_8, streams.stderrCharset)
        assertFalse(streams.stdoutIsConsole)
    }

    @Test
    fun `a console stream uses the charset of the console`() {
        val streams = ProcessStreams.detect(
            stdoutIsConsole = true,
            properties = properties("sun.stdout.encoding" to "Cp850", "sun.stderr.encoding" to "Cp850"),
        )

        val expected = System.console()?.charset() ?: charset("Cp850")
        assertEquals(expected, streams.stdoutCharset)
        assertTrue(streams.stdoutIsConsole)
    }

    @Test
    fun `a console charset that this java does not know falls back to the default`() {
        val streams = ProcessStreams.detect(
            stdoutIsConsole = true,
            properties = properties("sun.stdout.encoding" to "no-such-charset"),
        )

        assertEquals(System.console()?.charset() ?: java.nio.charset.Charset.defaultCharset(), streams.stdoutCharset)
    }

    private fun detect(
        feature: Int,
        stdoutIsConsole: Boolean,
        properties: (String) -> String? = properties(),
        probe: () -> Boolean? = { error("the operating system must not be asked") },
    ) = ProcessStreams.detect(stdoutIsConsole, properties, feature, probe)

    @Test
    fun `on java 17 standard error is a console when java says so`() {
        val consoleProperties = properties("sun.stderr.encoding" to "UTF-8")
        val withProperty = detect(17, stdoutIsConsole = false, properties = consoleProperties)
        val without = detect(17, stdoutIsConsole = true)

        assertTrue(withProperty.stderrIsConsole)
        assertFalse(without.stderrIsConsole)
    }

    @Test
    fun `from java 19 the operating system says whether standard error is a console`() {
        assertTrue(detect(21, stdoutIsConsole = false, probe = { true }).stderrIsConsole)
        assertFalse(detect(21, stdoutIsConsole = true, probe = { false }).stderrIsConsole)
    }

    @Test
    fun `from java 19 an unanswered question is settled by standard output`() {
        assertTrue(detect(21, stdoutIsConsole = true, probe = { null }).stderrIsConsole)
        assertFalse(detect(21, stdoutIsConsole = false, probe = { null }).stderrIsConsole)
    }

    @Test
    fun `from java 19 the encoding property says nothing about standard error`() {
        val streams = detect(21, false, properties("stderr.encoding" to "UTF-8", "sun.stderr.encoding" to "UTF-8")) {
            false
        }

        assertFalse(streams.stderrIsConsole)
    }

    @Test
    fun `standard error of the test process is not a console`() {
        assertNotEquals(true, StandardErrorConsole.probe())
    }
}
