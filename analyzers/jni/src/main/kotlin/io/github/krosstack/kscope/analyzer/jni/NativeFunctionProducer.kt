package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.natives.FunctionScanner
import io.github.krosstack.kscope.analyzer.jni.natives.NativeFunctionScanner
import io.github.krosstack.kscope.analyzer.jni.natives.RustFunctionScanner
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.ProducerDescriptor

internal const val NATIVE_FUNCTION_KIND = "jni.native-function"
internal const val ROLE_ATTRIBUTE = "jni.role"

/**
 * Turns each C, C++ or Rust file into the symbols of the JNI functions it defines or declares, found by the lexical
 * match of [NativeFunctionScanner] or [RustFunctionScanner]. Definitions and declarations are separate symbols, so
 * that a prototype in a header and the function in a source file are both addressable.
 */
internal class NativeFunctionProducer : SingleArtifactProducer() {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.jni.native-functions", "0.2.0"),
        consumes = NativeArtifactTypes.sources,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    override fun factsOf(unit: AnalysisUnit, artifact: Artifact, text: String): List<Fact> {
        val module = unit.module ?: return listOf(JniNotes.moduleUnknown(artifact))
        val facts = NativeFunctionFacts(SymbolAnchor(artifact, module, unit.sourceSet))
        return scannerFor(artifact, text).scan()
            .distinctBy { it.name to it.role }
            .map { Fact.OfSymbol(facts.symbolOf(it)) }
    }

    private fun scannerFor(artifact: Artifact, text: String): FunctionScanner =
        if (artifact.type == NativeArtifactTypes.rustSource) RustFunctionScanner(text) else NativeFunctionScanner(text)
}
