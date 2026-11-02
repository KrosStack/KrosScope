package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.document.YamlReader
import io.github.krosstack.kscope.analyzer.schema.generator.specs.SpecTarget
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiDocument
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiIdentities
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiReader
import io.github.krosstack.kscope.analyzer.schema.openapi.client.ClientPackages
import io.github.krosstack.kscope.analyzer.schema.openapi.client.OpenApiClientPlan
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

private const val GENERATOR = "openapi-generator"

/**
 * Makes the Kotlin client that the OpenAPI Generator makes of a document in a module that has it generate one: one
 * synthetic source for the API classes and one for the models. The classes are declared in the home of the module and
 * come from the operations and schemas of the document, whose ids name the module that holds the document.
 */
internal class OpenApiClientStubs(private val files: WorkspaceFiles) {
    private val reader = OpenApiReader()

    fun of(home: StubHome, target: SpecTarget, packages: ClientPackages): GeneratorOutcome {
        val document = documentOf(target.artifact) ?: return GeneratorOutcome.EMPTY
        val ids = OpenApiIdentities(target.placement.identity, target.artifact.ref.path.value)
        val client = OpenApiClientPlan(document, ids, packages).files()
        val prefix = "openapi-client/${target.virtualName}"
        return listOfNotNull(
            client.apis?.let { home.stubbed(it, GENERATOR, "$prefix.apis.kt") },
            client.models?.let { home.stubbed(it, GENERATOR, "$prefix.models.kt") },
        ).fold(GeneratorOutcome.EMPTY, GeneratorOutcome::plus)
    }

    private fun documentOf(artifact: Artifact): OpenApiDocument? = try {
        reader.read(YamlReader(files.read(artifact.ref).toString(Charsets.UTF_8)).read())
    } catch (_: IOException) {
        null
    }
}
