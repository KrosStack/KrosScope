package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol

internal const val PACKAGE = "sample.tasktide.nativebridge"

internal val crc32 = ExternalSpec(
    packageName = PACKAGE,
    types = listOf("Checksum"),
    method = "crc32",
    signature = "external fun crc32(data: ByteArray): Int",
    parameters = "ByteArray",
)

internal val nativeVersion = crc32.copy(
    method = "nativeVersion",
    signature = "external fun nativeVersion(): String",
    parameters = "",
)

internal fun shortName(method: String) = "Java_sample_tasktide_nativebridge_Checksum_$method"

/** Runs the linker over declarations and the native files of a test. */
internal object JniScenario {
    fun link(externals: List<Symbol>, native: Map<String, String> = mapOf(CHECKSUM_PATH to CHECKSUM_C)): Linked {
        val workspace = NativeWorkspace(native)
        val facts = externals.map { Fact.OfSymbol(it) } + workspace.produce().flatMap { it.facts }
        val graph = JniGraph(facts, workspace.artifacts)
        val out = mutableListOf<Fact>()
        JniLinker().link(graph) { out += it.facts }
        return Linked(graph, out)
    }

    fun external(spec: ExternalSpec, container: Symbol? = null): Symbol = Declarations.external(spec, container)
}
