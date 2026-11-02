package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.generator.specs.CheckoutRoots
import io.github.krosstack.kscope.analyzer.schema.generator.specs.SpecFiles
import io.github.krosstack.kscope.analyzer.schema.generator.specs.SpecResolver
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoReadings
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoTypeIndex
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.ProduceRequest

/**
 * What the generator inputs of all the build scripts of a workspace share: the artifacts of every repository, the
 * resolver of spec files, the makers of stubs, and which stubs are made already. The parts that read the spec files are
 * made when they are first needed, so that a workspace with no generator input pays nothing for them.
 */
internal class GeneratorServices(val request: ProduceRequest) {
    private val artifacts = (request.artifacts + request.units.flatMap { it.artifacts }).distinctBy { it.ref }
    private val made = HashSet<String>()
    private val readings by lazy { ProtoReadings(request) }

    val scripts: List<Artifact> = artifacts.filter { it.type == SchemaArtifactTypes.buildScript }
        .sortedWith(compareBy({ it.ref.repository.value }, { it.ref.path.value }))

    val resolver by lazy {
        SpecResolver(request.model, CheckoutRoots(request.files, artifacts), SpecFiles(request.files, artifacts))
    }

    val wire by lazy { WireInputStubs(readings, ProtoTypeIndex(readings.indexed())) }

    val client = OpenApiClientStubs(request.files)

    /** True the first time that a [key] is asked, false after. */
    fun firstTime(key: String): Boolean = made.add(key)
}
