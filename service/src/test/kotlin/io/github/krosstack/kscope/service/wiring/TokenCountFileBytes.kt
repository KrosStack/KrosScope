package io.github.krosstack.kscope.service.wiring

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

/** The bytes of a token count file as the format of version 1 lays them out, written and read without the cache. */
internal object TokenCountFileBytes {
    private const val MAGIC = 0x4B53544B43L
    private const val VERSION = 1

    fun write(file: Path, entries: Map<String, Int>, version: Int = VERSION) {
        DataOutputStream(Files.newOutputStream(file).buffered()).use { output ->
            output.writeLong(MAGIC)
            output.writeInt(version)
            output.writeInt(entries.size)
            entries.forEach { (key, value) ->
                output.writeUTF(key)
                output.writeInt(value)
            }
        }
    }

    fun read(file: Path): Map<String, Int> = DataInputStream(Files.newInputStream(file).buffered()).use { input ->
        check(input.readLong() == MAGIC) { "Not a token count file" }
        check(input.readInt() == VERSION) { "Another format version" }
        List(input.readInt()) { input.readUTF() to input.readInt() }.toMap()
    }
}
