@file:JvmName("Main")

package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import java.nio.file.Path
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val model = Path.of(args.single())
    val mode = model.resolve("mode").readText()
    if (mode == "multiline") {
        System.err.println("IllegalStateException: Native startup failed")
        repeat(24) { System.err.println("\tat worker.Native.start(Native.kt:42)") }
        System.err.println("\tCaused by: IllegalArgumentException: Missing runtime dependency")
        return
    }
    FakeEmbeddingMain.main(arrayOf(model.resolve("pid").toString(), mode))
}
