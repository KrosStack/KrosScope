package io.github.krosstack.kscope.cli

import com.github.ajalt.mordant.terminal.Terminal
import io.github.krosstack.kscope.cli.progress.ProgressScreen
import io.github.krosstack.kscope.cli.progress.TickerFactory
import java.nio.charset.Charset

/** The charset a stream is written in, and whether the stream is a console. */
internal data class StreamEncoding(val charset: Charset, val isConsole: Boolean)

/** The encodings that a run uses, as `doctor` reports them. [arguments] is the encoding of the command line. */
internal class EncodingReport(
    val stdout: StreamEncoding,
    val stderr: StreamEncoding,
    val defaultCharset: Charset,
    val arguments: String,
) {
    val argumentsAreUnicode: Boolean get() = arguments.uppercase().replace("-", "").startsWith("UTF")

    companion object {
        fun of(streams: ProcessStreams): EncodingReport = EncodingReport(
            StreamEncoding(streams.stdoutCharset, streams.stdoutIsConsole),
            StreamEncoding(streams.stderrCharset, streams.stderrIsConsole),
            Charset.defaultCharset(),
            System.getProperty("sun.jnu.encoding") ?: Charset.defaultCharset().name(),
        )

        val DETACHED = EncodingReport(
            StreamEncoding(Charsets.UTF_8, false),
            StreamEncoding(Charsets.UTF_8, false),
            Charsets.UTF_8,
            "UTF-8",
        )
    }
}

/**
 * What a run needs from the process around it, apart from standard output: the stream that progress is written to,
 * the clock, and the encodings. Tests replace all of it.
 */
internal class CliEnvironment(
    val screen: ProgressScreen,
    val clock: () -> Long,
    val tickers: TickerFactory,
    val encodings: EncodingReport,
    val terminal: Terminal? = null,
) {
    val lineSeparator: String get() = if (terminal == null) "\n" else System.lineSeparator()

    companion object {
        private const val DEFAULT_COLUMNS = 79

        /** A run that is not attached to a console and keeps its progress to itself. */
        fun detached(): CliEnvironment = CliEnvironment(
            screen = ProgressScreen(DEFAULT_COLUMNS, isConsole = false) {},
            clock = System::nanoTime,
            tickers = TickerFactory.NONE,
            encodings = EncodingReport.DETACHED,
        )

        fun system(streams: ProcessStreams, terminal: Terminal): CliEnvironment = CliEnvironment(
            screen = ProgressScreen(terminal.size.width, streams.stderrIsConsole) { text ->
                System.err.print(text)
                System.err.flush()
            },
            clock = System::nanoTime,
            tickers = TickerFactory.DAEMON_THREAD,
            encodings = EncodingReport.of(streams),
            terminal = terminal,
        )
    }
}
