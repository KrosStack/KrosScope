package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind

/** The inputs of a code generator that a build script gives: the kind of symbol each is, and the property it sets. */
internal enum class InputKind(val symbolKind: SymbolKind, val property: String) {
    OPENAPI_SPEC(SymbolKind("openapi-generator.input", SymbolCategory.BUILD), "openApiGenerate.inputSpec"),
    WIRE_SOURCE_PATH(SymbolKind("wire.source-path", SymbolCategory.BUILD), "wire.sourcePath"),
}
