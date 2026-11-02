package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.generator.script.InputKind
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptInput
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiIdentities
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoFileSymbol
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.BuildModel

/**
 * Finds the spec files that an input of a generator names, in any repository of the workspace. The path of the input
 * is made absolute from the directory of the build root or of the module in the checkout of the repository of the
 * script, normalized, and compared with the locations of the spec files. An OpenAPI input names one document; a Wire
 * input names a directory, or a file, and takes every proto file below it. The ids of the symbols of the files are the
 * ones their producers make, in the module and source set that holds the file.
 */
internal class SpecResolver(private val model: BuildModel, roots: CheckoutRoots, private val specs: SpecFiles) {
    private val locations = ScriptLocations(model, roots)

    fun targetsOf(input: ScriptInput, script: Artifact, module: Module): List<SpecTarget> {
        val location = locations.of(input.path, script.ref.repository, module)
        val artifacts = when {
            location == null -> emptyList()
            input.kind == InputKind.OPENAPI_SPEC -> listOfNotNull(specs.openApiAt(location))
            else -> specs.protosBelow(location)
        }
        return artifacts.mapNotNull(::targetOf)
    }

    private fun targetOf(artifact: Artifact): SpecTarget? = Placement.of(artifact, model)?.let { placement ->
        SpecTarget(artifact, placement, symbolOf(artifact, placement))
    }

    private fun symbolOf(artifact: Artifact, placement: Placement): SymbolId {
        val path = artifact.ref.path.value
        return if (artifact.type == SchemaArtifactTypes.openApi) {
            OpenApiIdentities(placement.identity, path).documentId()
        } else {
            ProtoFileSymbol.idOf(placement.identity, path)
        }
    }
}
