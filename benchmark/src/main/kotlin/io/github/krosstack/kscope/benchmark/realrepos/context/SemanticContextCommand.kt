package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.model.KscopeJson
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

internal fun runSemanticContextCommand(args: List<String>, print: (String) -> Unit): Int = try {
    val options = SemanticContextOptions.parse(args)
    val manifest = KscopeJson.decodeFromString<RealRepositoryManifest>(
        Files.readString(Path.of(options.flags.getValue("--manifest"))),
    )
    SemanticContextRun(options).run(manifest) { records ->
        records.forEach { record -> print("${record.task.id}: ${record.failure ?: record.unresolved ?: "complete"}") }
        val expected = options.tasks(manifest)
        if (SemanticContextReport.write(records, options.output, expected)) 0 else 1
    }
} catch (failure: IOException) {
    print("Semantic context benchmark failed: ${failure.message}")
    1
} catch (failure: UncheckedIOException) {
    print("Semantic context benchmark failed: ${failure.message}")
    1
} catch (failure: IllegalStateException) {
    print("Semantic context benchmark failed: ${failure.message}")
    1
} catch (failure: IllegalArgumentException) {
    print("Invalid semantic context benchmark: ${failure.message}")
    1
}
