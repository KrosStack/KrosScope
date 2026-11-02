package io.github.krosstack.kscope.cli.service.launch

import java.nio.file.Files
import java.nio.file.Path

internal object ArgumentEchoMain {
    const val OUTPUT_PROPERTY = "kscope.test.echo"
    const val SEPARATOR = "\u0001"

    @JvmStatic
    fun main(arguments: Array<String>) {
        val output = Path.of(checkNotNull(System.getProperty(OUTPUT_PROPERTY)) { "$OUTPUT_PROPERTY is not set" })
        Files.writeString(output, arguments.joinToString(SEPARATOR))
    }
}
