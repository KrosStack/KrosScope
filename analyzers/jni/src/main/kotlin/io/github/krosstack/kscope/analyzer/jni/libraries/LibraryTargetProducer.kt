package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.JniNotes
import io.github.krosstack.kscope.analyzer.jni.NativeArtifactTypes
import io.github.krosstack.kscope.analyzer.jni.SingleArtifactProducer
import io.github.krosstack.kscope.analyzer.jni.SymbolAnchor
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.ProducerDescriptor

/**
 * Turns each `CMakeLists.txt`, `Cargo.toml` and `Android.mk` into the symbols of the shared libraries it declares,
 * which the readers find by pattern. A file that declares none leaves no fact, and one that declares some while no
 * module contains it leaves a note.
 */
internal class LibraryTargetProducer : SingleArtifactProducer() {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.jni.library-targets", "0.1.0"),
        consumes = NativeArtifactTypes.buildFiles,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    private val readers = mapOf(
        NativeArtifactTypes.cmakeLists to CMakeLibraryReader(),
        NativeArtifactTypes.cargoManifest to CargoLibraryReader(),
        NativeArtifactTypes.ndkMakefile to NdkLibraryReader(),
    )

    override fun factsOf(unit: AnalysisUnit, artifact: Artifact, text: String): List<Fact> {
        val targets = readers[artifact.type]?.read(text).orEmpty().distinctBy { it.name }
        val module = unit.module
        return when {
            targets.isEmpty() -> emptyList()

            module == null -> listOf(JniNotes.moduleUnknown(artifact))

            else -> {
                val facts = LibraryTargetFacts(SymbolAnchor(artifact, module, unit.sourceSet))
                targets.map { Fact.OfSymbol(facts.symbolOf(it)) }
            }
        }
    }
}
