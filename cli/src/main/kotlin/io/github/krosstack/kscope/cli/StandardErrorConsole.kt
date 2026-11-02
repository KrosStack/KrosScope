package io.github.krosstack.kscope.cli

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * Asks the operating system whether standard error is attached to a console. Java reports it only up to release 18;
 * since release 19 the encoding property of standard error is set whatever the stream is, and the console of the
 * runtime knows about standard input and output alone.
 */
internal object StandardErrorConsole {
    private const val POSIX_STANDARD_ERROR = 2
    private const val WINDOWS_STANDARD_ERROR = -12
    private val NATIVE_NAMES = FunctionMapper { _, method -> method.name.replaceFirstChar(Char::uppercaseChar) }

    /** True or false as the operating system answers, or null when it cannot be asked. */
    fun probe(): Boolean? = try {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) windowsConsole() else posixTerminal()
    } catch (_: LinkageError) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun posixTerminal(): Boolean = Native.load("c", PosixLibrary::class.java).isatty(POSIX_STANDARD_ERROR) == 1

    private fun windowsConsole(): Boolean {
        val options = mapOf(Library.OPTION_FUNCTION_MAPPER to NATIVE_NAMES)
        val kernel = Native.load("kernel32", WindowsLibrary::class.java, options)
        val handle = kernel.getStdHandle(WINDOWS_STANDARD_ERROR)
        return handle != null && kernel.getConsoleMode(handle, IntByReference())
    }

    internal interface PosixLibrary : Library {
        fun isatty(fileDescriptor: Int): Int
    }

    internal interface WindowsLibrary : Library {
        fun getStdHandle(standardHandle: Int): Pointer?

        fun getConsoleMode(handle: Pointer, mode: IntByReference): Boolean
    }
}
