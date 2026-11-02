package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.ProduceRequest
import java.io.IOException

/** The proto files of a request, each read and parsed once, whether the request asks for it or only provides it. */
internal class ProtoReadings(private val request: ProduceRequest) {
    private val reader = ProtoFileReader()
    private val parsed = HashMap<ArtifactRef, ProtoFile?>()

    /** Null when the file cannot be read or parsed. */
    fun of(artifact: Artifact): ProtoFile? = parsed.getOrPut(artifact.ref) { parse(artifact) }

    /** Every proto file of the workspace that could be parsed, with the ids of its symbols. */
    fun indexed(): List<IndexedProto> = (request.artifacts + request.units.flatMap { it.artifacts })
        .filter { it.type == SchemaArtifactTypes.proto }
        .distinctBy { it.ref }
        .sortedWith(compareBy({ it.ref.repository.value }, { it.ref.path.value }))
        .mapNotNull { artifact ->
            of(artifact)?.let { file ->
                val placement = Placement.of(artifact, request.model)
                IndexedProto(file, placement?.let { ProtoIdentities(it.identity, file.packageName) })
            }
        }

    private fun parse(artifact: Artifact): ProtoFile? = try {
        val text = request.files.read(artifact.ref).toString(Charsets.UTF_8)
        (reader.read(artifact.ref.path.value, text) as? ProtoReading.Parsed)?.file
    } catch (_: IOException) {
        null
    }
}
