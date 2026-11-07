package io.github.krosstack.kscope.cli.service.launch

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.WString
import java.io.IOException
import java.nio.file.Path

internal object WindowsProcessCreation {
    private const val DETACHED_PROCESS = 0x00000008
    private const val CREATE_NEW_PROCESS_GROUP = 0x00000200
    private const val CREATE_BREAKAWAY_FROM_JOB = 0x01000000
    private const val ERROR_ACCESS_DENIED = 5
    private const val CREATION_FLAGS_INDEX = 5
    private const val STARTUP_SIZE_32 = 68L
    private const val STARTUP_SIZE_64 = 104L
    private const val ID_BYTES = 8L
    private const val POINTERS = 2L
    private val QUOTED_END = Regex("(\\\\*)(\"|$)")
    private val closeHandle = NativeLibrary.getInstance("kernel32")
        .getFunction("CloseHandle", Function.ALT_CONVENTION)
    private val createProcess = NativeLibrary.getInstance("kernel32")
        .getFunction("CreateProcessW", Function.ALT_CONVENTION)

    fun start(command: List<String>, workingDirectory: Path): Long {
        val detached = DETACHED_PROCESS or CREATE_NEW_PROCESS_GROUP
        Memory(if (Native.POINTER_SIZE == Long.SIZE_BYTES) STARTUP_SIZE_64 else STARTUP_SIZE_32).use { startup ->
            Memory(POINTERS * Native.POINTER_SIZE + ID_BYTES).use { information ->
                startup.clear()
                startup.setInt(0, startup.size().toInt())
                val arguments = arrayOf(
                    null, null, null, null, 0, detached or CREATE_BREAKAWAY_FROM_JOB, null,
                    WString(workingDirectory.toAbsolutePath().toString()), startup, information,
                )
                var error = create(command, arguments)
                if (error == ERROR_ACCESS_DENIED) {
                    arguments[CREATION_FLAGS_INDEX] = detached
                    error = create(command, arguments)
                }
                if (error != 0) throw IOException("CreateProcessW failed with Windows error $error.")
                try {
                    return Integer.toUnsignedLong(information.getInt(POINTERS * Native.POINTER_SIZE))
                } finally {
                    closeHandle.invokeInt(arrayOf(information.getPointer(Native.POINTER_SIZE.toLong())))
                    closeHandle.invokeInt(arrayOf(information.getPointer(0)))
                }
            }
        }
    }

    private fun create(command: List<String>, arguments: Array<Any?>): Int {
        val line = command.joinToString(" ", transform = ::quoted)
        Memory((line.length + 1L) * Native.WCHAR_SIZE).use { buffer ->
            buffer.setWideString(0, line)
            arguments[1] = buffer
            val created = createProcess.invokeInt(arguments)
            val error = Native.getLastError()
            return if (created != 0) 0 else error
        }
    }

    private fun quoted(argument: String): String = "\"" + QUOTED_END.replace(argument) { match ->
        match.groupValues[1].repeat(2) + if (match.groupValues[2].isEmpty()) "" else "\\\""
    } + "\""
}
