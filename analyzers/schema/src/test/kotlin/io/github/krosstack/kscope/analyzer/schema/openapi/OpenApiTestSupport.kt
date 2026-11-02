package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.Resources
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.document.YamlReader
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Symbol

internal const val LIBRARY_PATH = "spec/openapi/library.yaml"

/** The sample document of a library API, in a module of its own. */
internal fun libraryWorkspace() = TestWorkspace(
    mapOf(LIBRARY_PATH to Resources.text("openapi/library.yaml")),
    listOf(ModuleSpec(":spec")),
)

internal fun TestWorkspace.openApiSymbols(): List<Symbol> = produce(OpenApiProducer()).facts().symbols()

internal fun Symbol.line(): Int = requireNotNull(nameLocation).range.startLine

internal fun Symbol.lines(): Pair<Int, Int> = requireNotNull(declaration).range.let { it.startLine to it.endLine }

internal fun documentOf(text: String): OpenApiDocument =
    checkNotNull(OpenApiReader().read(YamlReader(text).read())) { "No document in: $text" }
