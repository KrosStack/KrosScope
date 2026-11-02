package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.schema.ApiSchemas
import java.nio.file.Path

internal fun runSchemasCommand(args: List<String>, print: (String) -> Unit): Int {
    if (args.size != ARGUMENTS || args[1] != "--out") {
        print("usage: schemas --out <directory>")
        return USAGE_ERROR
    }
    val count = ApiSchemas.write(Path.of(args[2]))
    print("Generated $count API schemas.")
    return 0
}

private const val ARGUMENTS = 3
private const val USAGE_ERROR = 2
