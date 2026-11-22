package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.semantic.SemanticContextGate
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal object SemanticContextReport {
    fun write(
        records: List<SemanticContextRecord>,
        output: Path,
        expected: List<RealCommitTask> = records.map { it.task },
    ): Boolean {
        val result = SemanticContextGate.evaluate(records, expected)
        val json = buildJsonObject {
            put("expected", expected.size)
            put("complete", records.count { it.complete })
            put("regressionPassed", result.regressionPassed)
            put("verdict", KscopeJson.encodeToJsonElement(result))
            put(
                "pipelines",
                KscopeJson.encodeToJsonElement(
                    records.mapNotNull { it.pipeline }.distinct()
                        .sortedBy { it.fingerprint().value },
                ),
            )
        }
        Files.createDirectories(output)
        publish(output.resolve("semantic-context-report.json"), "$json\n")
        publish(output.resolve("semantic-context-report.md"), SemanticContextGate.markdown(result))
        return result.passed
    }

    private fun publish(target: Path, contents: String) {
        val temporary = Files.createTempFile(target.parent, ".context-", ".pending")
        try {
            Files.writeString(temporary, contents)
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
