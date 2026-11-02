package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.NativeArtifactTypes
import io.github.krosstack.kscope.analyzer.jni.loads.LOAD_LIBRARY_KIND
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val NATIVE_LIBRARY_SUB_KIND = "native-library"
internal const val NATIVE_BUILD_NOT_ANALYSED = "jni.native-build-not-analysed"
internal const val PRODUCER_NOT_IN_WORKSPACE = "artifact.producer-not-in-workspace"
internal const val AMBIGUOUS_PRODUCER = "artifact.ambiguous-producer"
private const val ALL = Int.MAX_VALUE

/**
 * Joins every call that loads a native library to the library target that builds it, in any repository of the
 * workspace. The name in the call is the name of the library, which a build names its target after by convention, so
 * the evidence is `convention`, and the binding is probable at best: a target of that name only builds a file of that
 * name when nothing renames its output. It is probable when exactly one target of the name is a shared library, and
 * heuristic when the build leaves the type open. Several targets of the name are an unresolved ambiguity; none, in a
 * workspace with native build files, is a producer that is not in the workspace; a workspace without native build files
 * says that the native build was not looked at.
 */
internal class LibraryLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.jni.library-linker", "0.1.0"),
        consumes = NativeArtifactTypes.buildFiles,
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun link(graph: GraphReader, sink: FactSink) {
        val loads = graph.symbols(SymbolQuery(kindId = LOAD_LIBRARY_KIND, limit = ALL)).sortedBy { it.id.value }
        if (loads.isEmpty()) return
        val libraries = Libraries(graph)
        val facts = loads.map { Fact.OfRelationship(libraries.binding(it)) }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }
}

/** The library targets of the workspace by name, and whether it has any native build file at all. */
private class Libraries(graph: GraphReader) {
    private val hasBuildFiles = graph.artifacts().any { it.type in NativeArtifactTypes.buildFiles }
    private val byName: Map<String, List<Symbol>> = LibraryKinds.all
        .flatMap { graph.symbols(SymbolQuery(kindId = it, limit = ALL)) }
        .groupBy { it.name }

    fun binding(load: Symbol): Relationship {
        val targets = byName[load.name].orEmpty().sortedBy { it.id.value }
        return Relationship(
            from = NodeRef.OfSymbol(load.id),
            kind = RelationKind.BINDS,
            subKind = NATIVE_LIBRARY_SUB_KIND,
            resolution = resolutionOf(load.name, targets),
            evidenceClass = EvidenceClass.CONVENTION,
            site = load.declaration,
            scope = load.scope,
        )
    }

    private fun resolutionOf(name: String, targets: List<Symbol>): Resolution = when {
        targets.size == 1 -> Resolution.Resolved(NodeRef.OfSymbol(targets.single().id), certaintyOf(targets.single()))
        targets.size > 1 -> Resolution.Unresolved(name, ReasonCode(AMBIGUOUS_PRODUCER))
        hasBuildFiles -> Resolution.Unresolved(name, ReasonCode(PRODUCER_NOT_IN_WORKSPACE))
        else -> Resolution.NotAnalysed(name, ReasonCode(NATIVE_BUILD_NOT_ANALYSED))
    }

    private fun certaintyOf(target: Symbol) =
        if (target.certainty == Certainty.EXACT) Certainty.PROBABLE else Certainty.HEURISTIC
}
