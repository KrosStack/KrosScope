package io.github.krosstack.kscope.cli

import java.nio.file.Path
import kotlin.test.assertEquals

/** A file nine directories deep in the greeter workspace, so that its path is longer than a terminal line. */
internal const val DEEP_PATH = "lib/src/main/kotlin/deeply/nested/package/with/many/directories/GreeterHelperFactory.kt"

/** The greeter workspace with one deeply nested class, scanned; [block] gets its root. */
internal fun inScannedDeepWorkspace(block: (Path) -> Unit) = inGreeterWorkspace { root ->
    writeFile(root, DEEP_PATH, "package greet\n\nclass GreeterHelperFactory\n")
    assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
    block(root)
}

/** Runs the command on [root] with captured output and returns what it printed; the exit code must be 0. */
internal fun printedAt(root: Path, vararg arguments: String): String {
    val result = runKscopeWith(arguments.toList() + listOf("--workspace", root.toString()))
    assertEquals(0, result.statusCode, result.output)
    return result.stdout
}
