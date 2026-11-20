package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.model.KscopeJson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal fun runSemanticCommand(args: List<String>, print: (String) -> Unit): Int = try {
    val options = SemanticOptions.parse(args)
    val manifest = KscopeJson.decodeFromString<RealRepositoryManifest>(
        Files.readString(Path.of(options.flags.getValue("--manifest"))),
    )
    SemanticWorker(options).use { provider ->
        val records = SemanticRun(options, provider).run(manifest)
        SemanticCheckpoint.write(options.output.resolve("pipeline.json"), provider.recipe)
        print(SemanticReport.write(records, options.output))
        if (records.all { it.failure == null }) 0 else 1
    }
} catch (failure: IOException) {
    System.err.println("Semantic benchmark failed: ${failure.message}")
    1
} catch (failure: IllegalArgumentException) {
    System.err.println("Invalid semantic benchmark: ${failure.message}")
    1
} catch (failure: IllegalStateException) {
    System.err.println("Semantic benchmark failed: ${failure.message}")
    1
}
