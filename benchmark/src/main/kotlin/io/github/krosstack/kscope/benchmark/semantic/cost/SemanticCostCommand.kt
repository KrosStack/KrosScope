package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.encodeToJsonElement
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal fun runSemanticCostCommand(args: List<String>, print: (String) -> Unit): Int = try {
    val options = SemanticCostOptions.parse(args)
    val result = SemanticCostProbe(options).run()
    Files.createDirectories(options.output)
    val temporary = Files.createTempFile(options.output, ".cost-", ".pending")
    try {
        Files.writeString(temporary, KscopeJson.encodeToJsonElement(result).toString() + "\n")
        Files.move(temporary, options.output.resolve("semantic-cost.json"), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
    result.notRun.forEach(print)
    print("Service samples: ${result.samples.size}; rejected: ${result.samples.count { !it.accepted }}.")
    val active = options.workload?.activeIndexing != true ||
        result.samples.any { it.boundary == SemanticCostProbe.Boundary.ACTIVE_INDEXING }
    if (active && result.samples.isNotEmpty() && result.samples.all { it.accepted }) 0 else 1
} catch (failure: IOException) {
    print("Semantic cost probe failed: ${failure.message}")
    1
} catch (failure: UncheckedIOException) {
    print("Semantic cost probe failed: ${failure.message}")
    1
} catch (failure: IllegalArgumentException) {
    print("Invalid semantic cost probe: ${failure.message}")
    1
} catch (failure: IllegalStateException) {
    print("Semantic cost probe failed: ${failure.message}")
    1
}
