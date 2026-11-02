package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationSuiteResult
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.GraphResult
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.retrieval.RelatedResult
import io.github.krosstack.kscope.retrieval.SymbolResult
import io.github.krosstack.kscope.service.DoctorResult
import io.github.krosstack.kscope.service.InfoResult
import io.github.krosstack.kscope.service.NodeResolution
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.StructureResult
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

    private class Document(val descriptor: SerialDescriptor, val level: LevelField = LevelField.REQUIRED)

    fun documents(): Map<String, String> = sortedMapOf(
        "analysis" to Document(AnalysisResult.serializer().descriptor),
        "context" to Document(ContextPackage.serializer().descriptor),
        "context-benchmark" to Document(ContextBenchmarkResult.serializer().descriptor, LevelField.NONE),
        "doctor" to Document(DoctorResult.serializer().descriptor, LevelField.NONE),
        "graph" to Document(GraphResult.serializer().descriptor),
        "impact" to Document(ImpactResult.serializer().descriptor),
        "info" to Document(InfoResult.serializer().descriptor, LevelField.NULLABLE),
        "localization" to Document(LocalizationSuiteResult.serializer().descriptor, LevelField.NONE),
        "node-resolution" to Document(NodeResolution.serializer().descriptor),
        "related" to Document(RelatedResult.serializer().descriptor),
        "scan" to Document(ScanResult.serializer().descriptor),
        "structure" to Document(StructureResult.serializer().descriptor, LevelField.NONE),
        "symbol" to Document(SymbolResult.serializer().descriptor),
    ).mapValues { (name, document) ->
        json.encodeToString(DescriptorSchemas.document(name, document.descriptor, document.level)) + "\n"
    }
}
