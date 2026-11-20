package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealIdentifier
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.measurement.canonicalFuturePath
import java.nio.file.Path

internal class SemanticOptions(val flags: Map<String, String>) {
    val output: Path = Path.of(flags.getValue("--out"))
    val scratch: Path = Path.of(flags.getValue("--scratch"))
    val split: RealSplit = if (flags.getValue("--split") == "dev") RealSplit.DEV else RealSplit.TEST
    val repositories: Set<String> = flags["--repositories"]?.split(',')?.toSet().orEmpty()
    val replayTasks: Set<String> = flags["--replay-tasks"]?.split(',')?.toSet().orEmpty()

    init {
        val replayFlags = setOf("--replay-source", "--replay-out", "--replay-tasks")
        if (flags.keys.any { it in replayFlags }) {
            require(flags.keys.containsAll(replayFlags) && split == RealSplit.DEV)
            require(replayTasks.isNotEmpty() && replayTasks.all(RealIdentifier::isValid))
            val source = canonicalFuturePath(Path.of(flags.getValue("--replay-source")))
            listOf(output, scratch, Path.of(flags.getValue("--replay-out"))).forEach {
                val destination = canonicalFuturePath(it)
                require(!destination.startsWith(source) && !source.startsWith(destination))
            }
        }
    }

    companion object {
        fun parse(args: List<String>): SemanticOptions {
            val pairs = args.drop(1).chunked(2)
            require(pairs.all { it.size == 2 && it.last().isNotBlank() && !it.last().startsWith("--") })
            val flags = pairs.associate { it.first() to it.last() }
            val required = setOf(
                "--manifest", "--out", "--scratch", "--split", "--checkpoint-key",
                "--paraphrases", "--worker", "--model", "--device",
            )
            require(flags.size == pairs.size && flags.keys.containsAll(required))
            require(
                (required + setOf("--repositories", "--replay-source", "--replay-out", "--replay-tasks"))
                    .containsAll(flags.keys),
            )
            require(flags["--split"] in setOf("dev", "test") && flags["--device"] in setOf("cpu", "cuda"))
            require(Regex("[0-9a-f]{64}").matches(flags.getValue("--checkpoint-key")))
            return SemanticOptions(flags)
        }
    }
}
