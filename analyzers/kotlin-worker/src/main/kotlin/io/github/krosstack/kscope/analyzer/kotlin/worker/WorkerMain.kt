package io.github.krosstack.kscope.analyzer.kotlin.worker

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Entry point of the child JVM. Standard output carries protocol lines only; anything else the process or its
 * libraries print is redirected to standard error. The process ends with `halt`, because a non-daemon IntelliJ
 * thread would otherwise keep the JVM alive after the analysis finished.
 */
object WorkerMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val protocol = BufferedWriter(OutputStreamWriter(FileOutputStream(FileDescriptor.out), Charsets.UTF_8))
        System.setOut(System.err)
        val code = runCatching { serve(args, protocol) }.getOrElse {
            it.printStackTrace()
            EXIT_FAILED
        }
        runCatching { protocol.flush() }
        System.err.flush()
        Runtime.getRuntime().halt(code)
    }

    private fun serve(arguments: Array<String>, protocol: BufferedWriter): Int {
        check(arguments.isEmpty()) { "The worker takes no arguments" }
        val input = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
        val writer = ResponseWriter(protocol)
        return WorkerLoop(input, writer, WorkerService(writer)).run()
    }
}
