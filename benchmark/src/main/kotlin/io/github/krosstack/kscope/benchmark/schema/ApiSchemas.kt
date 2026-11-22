package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationSuiteResult
import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.retrieval.GraphResult
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.retrieval.RelatedResult
import io.github.krosstack.kscope.retrieval.SymbolResult
import io.github.krosstack.kscope.service.DoctorResult
import io.github.krosstack.kscope.service.InfoResult
import io.github.krosstack.kscope.service.NodeResolution
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.StructureResult
import io.github.krosstack.kscope.service.analysis.AnalysisJson
import io.github.krosstack.kscope.service.status.StatusResult
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

internal object ApiSchemas {
    private val json = Json { prettyPrint = true }

    fun write(directory: Path): Int {
        val documents = documents()
        Files.createDirectories(directory)
        documents.forEach { (name, schema) ->
            Files.writeString(directory.resolve("$name.schema.json"), schema)
        }
        return documents.size
    }

    private class Document(
        val descriptor: SerialDescriptor,
        val level: LevelField = LevelField.REQUIRED,
        val dated: Boolean = false,
        val narrowed: Boolean = false,
    )

    fun documents(): Map<String, String> = sortedMapOf(
        "analysis" to Document(AnalysisJson.serializer().descriptor, dated = true, narrowed = true),
        "context" to Document(ContextPackage.serializer().descriptor, dated = true, narrowed = true),
        "context-benchmark" to Document(ContextBenchmarkResult.serializer().descriptor, LevelField.NONE),
        "diagram" to Document(Diagram.serializer().descriptor),
        "doctor" to Document(DoctorResult.serializer().descriptor, LevelField.NONE),
        "graph" to Document(GraphResult.serializer().descriptor),
        "impact" to Document(ImpactResult.serializer().descriptor, dated = true),
        "info" to Document(InfoResult.serializer().descriptor, LevelField.NULLABLE),
        "localization" to Document(LocalizationSuiteResult.serializer().descriptor, LevelField.NONE),
        "node-resolution" to Document(NodeResolution.serializer().descriptor, narrowed = true),
        "related" to Document(RelatedResult.serializer().descriptor),
        "scan" to Document(ScanResult.serializer().descriptor),
        "status" to Document(StatusResult.serializer().descriptor, LevelField.NULLABLE),
        "structure" to Document(StructureResult.serializer().descriptor, LevelField.NONE),
        "symbol" to Document(SymbolResult.serializer().descriptor, narrowed = true),
    ).mapValues { (name, document) ->
        val schema = DescriptorSchemas.document(
            name,
            document.descriptor,
            document.level,
            document.dated,
            document.narrowed,
        )
        json.encodeToString(schema) + "\n"
    }
}
