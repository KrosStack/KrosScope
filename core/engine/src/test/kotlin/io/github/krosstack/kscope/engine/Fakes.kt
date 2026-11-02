package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
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
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.InputBasis
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.RepositoryCheckout
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.graph.GraphReader

internal val kotlinSource = ArtifactType("kotlin.source", ArtifactCategory.SOURCE)
internal val buildScript = ArtifactType("gradle.build-script", ArtifactCategory.BUILD)

internal fun descriptorOf(id: String, consumes: ArtifactType, granularity: OwnershipKind) = ProducerDescriptor(
    ProducerRef(id, "1"),
    setOf(consumes),
    setOf(AnalyzerCapability.DECLARATIONS),
    granularity,
)

private fun note(code: String, message: String) =
    Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.INFO, ReasonCode(code), message, null))

private fun batchOf(producer: ProducerRef, owner: OwnershipUnit, vararg facts: Fact) =
    FactBatch(producer, owner, Fingerprint("ignored"), facts.asList())

/** Emits one diagnostic per requested unit and remembers which units each call received. */
internal class RecordingProducer(
    id: String,
    consumes: ArtifactType = kotlinSource,
    granularity: OwnershipKind = OwnershipKind.ARTIFACT,
) : FactProducer {
    override val descriptor = descriptorOf(id, consumes, granularity)
    val calls = mutableListOf<List<String>>()
    var syntheticSources: List<SyntheticSource> = emptyList()
    var classpaths: Map<SourceSetId, List<String>> = emptyMap()
    var contextPaths: List<String> = emptyList()
    var model: BuildModel? = null

    val producedKeys: List<String> get() = calls.flatten()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        calls += request.units.map { it.owner.key }
        syntheticSources = request.syntheticSources
        classpaths = request.classpaths
        contextPaths = request.artifacts.map { it.ref.path.value }
        model = request.model
        request.units.forEach { unit ->
            val paths = unit.artifacts.joinToString(",") { it.ref.path.value }
            sink.accept(batchOf(descriptor.producer, unit.owner, note("fake.produced", "${unit.owner.key} <- $paths")))
        }
    }
}

/** Names another producer in its batches and answers an owner that was never requested. */
internal class MisbehavingProducer(id: String) : FactProducer {
    override val descriptor = descriptorOf(id, kotlinSource, OwnershipKind.ARTIFACT)

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            sink.accept(batchOf(ProducerRef("impostor", "1"), unit.owner, note("fake.bad", "x")))
        }
        sink.accept(batchOf(descriptor.producer, OwnershipUnit(OwnershipKind.ARTIFACT, "unrequested")))
    }
}

internal class RecordingLinker(id: String) : BoundaryLinker {
    override val descriptor = descriptorOf(id, kotlinSource, OwnershipKind.WORKSPACE)
    var linkCalls = 0
        private set

    override fun link(graph: GraphReader, sink: FactSink) {
        linkCalls++
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "ignored")
        sink.accept(batchOf(descriptor.producer, owner, note("fake.linked", "artifacts=${graph.artifacts().size}")))
    }
}

/** Emits one synthetic stub per requested module, in that module's first source set. */
internal class RecordingSynthesizer(private val id: String) : SurfaceSynthesizer {
    override val descriptor = descriptorOf(id, buildScript, OwnershipKind.MODULE)
    var synthesizeCalls = 0
        private set

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> {
        synthesizeCalls++
        return request.units.mapNotNull { unit -> stubFor(request, unit)?.also { announce(unit, sink) } }
    }

    private fun announce(unit: AnalysisUnit, sink: FactSink) {
        sink.accept(batchOf(descriptor.producer, unit.owner, note("fake.synthesized", unit.owner.key)))
    }

    private fun stubFor(request: ProduceRequest, unit: AnalysisUnit): SyntheticSource? {
        val module = unit.module
        val sourceSet = request.model.sourceSets.firstOrNull { it.module == module?.id }
        if (module == null || sourceSet == null) return null
        val text = "// stub " + unit.artifacts.joinToString { it.contentHash.value }
        return SyntheticSource("synthetic/${module.path.trim(':')}/Generated.kt", "kotlin", text, sourceSet.id, id)
    }
}

/** The structural model plus an edge that makes the `:app` module depend on the `:core` module. */
internal class AppDependsOnCoreProvider(
    private val delegate: StructuralBuildModelProvider = StructuralBuildModelProvider(),
) : BuildModelProvider {
    override val producer = ProducerRef("test.app-depends-on-core", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val result = delegate.provide(request) as? BuildModelResult.Provided ?: return BuildModelResult.NotApplicable
        val modules = result.model.modules.associateBy { it.path }
        val edge = Relationship(
            from = NodeRef.OfModule(modules.getValue(":app").id),
            kind = RelationKind.DEPENDS_ON,
            subKind = null,
            resolution = Resolution.Resolved(NodeRef.OfModule(modules.getValue(":core").id), Certainty.EXACT),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(),
        )
        return result.copy(model = result.model.copy(dependencies = listOf(edge)))
    }
}

/** A provider that executes build logic: the structural model, or the given outcome, plus fixed classpaths. */
internal class FakeTrustedProvider(
    id: String = "test.trusted",
    private val delegate: StructuralBuildModelProvider = StructuralBuildModelProvider(),
) : BuildModelProvider {
    override val producer = ProducerRef(id, "1")
    override val requiresTrust = true
    var failure: BuildModelResult.Failed? = null
    var classpaths: (BuildModel) -> Map<SourceSetId, List<String>> = { emptyMap() }
    var calls = 0
        private set

    override fun provide(request: BuildModelRequest): BuildModelResult {
        calls++
        val provided = delegate.provide(request) as? BuildModelResult.Provided
        return failure ?: provided?.copy(classpaths = classpaths(provided.model)) ?: BuildModelResult.NotApplicable
    }
}

internal const val FAKE_NAMESPACE = "fake.ns"

/** Emits one symbolic reference per artifact; its key is the first line of the file, so editing the file changes it. */
internal class ReferencingProducer(id: String, private val namespace: String = FAKE_NAMESPACE) : FactProducer {
    override val descriptor = descriptorOf(id, kotlinSource, OwnershipKind.ARTIFACT)

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val key = String(request.files.read(artifact.ref)).lineSequence().first().trim()
            val reference = SymbolicReference(
                from = NodeRef.OfArtifact(artifact.ref),
                kind = RelationKind.REFERENCES,
                subKind = null,
                namespace = namespace,
                key = key,
                site = SourceLocation(artifact.ref, TextRange(1, 1, 1, key.length + 1)),
                scope = Scope(sourceSet = artifact.sourceSet),
                evidenceClass = EvidenceClass.TEXTUAL,
            )
            sink.accept(batchOf(descriptor.producer, unit.owner, Fact.OfSymbolicReference(reference)))
        }
    }
}

/** Leaves the references whose key starts with `skip` unanswered and answers those starting with `twice` twice. */
internal class SloppyLinker(id: String) : AnsweringLinker(id) {
    override fun answersTo(reference: SymbolicReference): List<Relationship> = when {
        reference.key.startsWith("skip") -> emptyList()
        reference.key.startsWith("twice") -> List(2) { super.answersTo(reference).single() }
        else -> super.answersTo(reference)
    }
}

/** One class symbol per source file, named after the file, declared in it. */
internal class FileNamedSymbolProducer(id: String) : FactProducer {
    override val descriptor = descriptorOf(id, kotlinSource, OwnershipKind.ARTIFACT)

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val name = artifact.ref.path.value.substringAfterLast('/').substringBeforeLast('.')
            val symbol = Samples.symbol.copy(
                id = SymbolId("test:${artifact.ref.path.value}"),
                kind = SymbolKind("kotlin.class", SymbolCategory.TYPE),
                name = name,
                qualifiedName = null,
                signature = null,
                documentation = null,
                declaration = SourceLocation(artifact.ref, TextRange(1, 1, 1, 2)),
            )
            sink.accept(batchOf(descriptor.producer, unit.owner, Fact.OfSymbol(symbol)))
        }
    }
}

/** Reads the repository revision, so it has one unit with every artifact. */
internal class RevisionProducer(id: String) : FactProducer {
    override val descriptor = ProducerDescriptor(
        ProducerRef(id, "1"),
        emptySet(),
        emptySet(),
        OwnershipKind.REPOSITORY,
        InputBasis.REPOSITORY_REVISION,
    )
    val units = mutableListOf<List<String>>()
    val checkouts = mutableListOf<RepositoryCheckout?>()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        checkouts += request.checkout
        request.units.forEach { unit ->
            units += unit.artifacts.map { it.ref.path.value }
            sink.accept(batchOf(descriptor.producer, unit.owner, note("fake.revision", unit.owner.key)))
        }
    }
}
