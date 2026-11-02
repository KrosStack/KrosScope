package io.github.krosstack.kscope.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset

/**
 * The encodings of standard output and standard error. A stream that is redirected carries UTF-8, so that captured
 * JSON and Markdown are valid whatever the platform default is; a stream that is a console uses the charset of the
 * console, which is what it can display.
 */
internal class ProcessStreams(
    val stdoutIsConsole: Boolean,
    val stderrIsConsole: Boolean,
    val stdoutCharset: Charset,
    val stderrCharset: Charset,
) {
    fun install() {
        System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, stdoutCharset))
        System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, stderrCharset))
    }

    companion object {
        private const val FIRST_RELEASE_WITH_STDERR_ENCODING_ALWAYS_SET = 19

        /**
         * [stdoutIsConsole] comes from the terminal library, which asks the operating system. Up to Java 18 the
         * `sun.stderr.encoding` property is set only for a console. Later releases set it for every stream, so
         * [stderrProbe] asks the operating system; when it cannot answer, standard error is taken to be a console
         * when standard output is.
         */
        fun detect(
            stdoutIsConsole: Boolean,
            properties: (String) -> String? = System::getProperty,
            javaFeature: Int = Runtime.version().feature(),
            stderrProbe: () -> Boolean? = StandardErrorConsole::probe,
        ): ProcessStreams {
            val stderrIsConsole = if (javaFeature < FIRST_RELEASE_WITH_STDERR_ENCODING_ALWAYS_SET) {
                properties("sun.stderr.encoding") != null
            } else {
                stderrProbe() ?: stdoutIsConsole
            }
            return ProcessStreams(
                stdoutIsConsole,
                stderrIsConsole,
                charsetOf(stdoutIsConsole, properties("sun.stdout.encoding") ?: properties("stdout.encoding")),
                charsetOf(stderrIsConsole, properties("sun.stderr.encoding") ?: properties("stderr.encoding")),
            )
        }

        private fun charsetOf(isConsole: Boolean, name: String?): Charset = when {
            !isConsole -> Charsets.UTF_8
            System.console() != null -> System.console().charset()
            name != null && Charset.isSupported(name) -> Charset.forName(name)
            else -> Charset.defaultCharset()
        }
    }
}
