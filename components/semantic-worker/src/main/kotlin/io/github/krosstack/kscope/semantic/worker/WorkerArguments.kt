package io.github.krosstack.kscope.semantic.worker

import java.nio.file.Path

internal class WorkerArguments(args: Array<String>) {
    init {
        require(args.size in 1..2) { "Expected model directory and optional device (cpu or cuda)" }
    }

    val model: Path = Path.of(args[0])
    val device: String = args.getOrElse(1) { "cpu" }.also {
        require(it in setOf("cpu", "cuda")) { "Unsupported device: $it; expected cpu or cuda" }
    }
}
