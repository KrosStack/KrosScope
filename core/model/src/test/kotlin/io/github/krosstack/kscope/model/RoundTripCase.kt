package io.github.krosstack.kscope.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

internal class RoundTripCase<T>(val name: String, val serializer: KSerializer<T>, val value: T)

private fun <T> case(name: String, serializer: KSerializer<T>, value: T) = RoundTripCase(name, serializer, value)

private val identifierCases: List<RoundTripCase<*>> = listOf(
    case("RepositoryId", RepositoryId.serializer(), Samples.repositoryId),
    case("BuildRootId", BuildRootId.serializer(), Samples.buildRootId),
    case("ModuleId", ModuleId.serializer(), Samples.moduleId),
    case("SourceSetId", SourceSetId.serializer(), Samples.sourceSetId),
    case("TargetId", TargetId.serializer(), Samples.targetId),
    case("VariantId", VariantId.serializer(), Samples.variantId),
    case("SymbolId", SymbolId.serializer(), Samples.symbolId),
    case("RepoPath", RepoPath.serializer(), Samples.artifactRef.path),
    case("ContentHash", ContentHash.serializer(), Samples.contentHash),
    case("Fingerprint", Fingerprint.serializer(), Samples.fingerprint),
    case("ReasonCode", ReasonCode.serializer(), ReasonCode("kotlin.unresolved-reference")),
    case("SourceRootKind", SourceRootKind.serializer(), SourceRootKind("android.res")),
    case("ArtifactRef", ArtifactRef.serializer(), Samples.artifactRef),
    case("TextRange", TextRange.serializer(), Samples.range),
    case("SourceLocation", SourceLocation.serializer(), Samples.location),
    case("Scope", Scope.serializer(), Samples.scope),
    case("Scope everywhere", Scope.serializer(), Scope()),
)

private val enumCases: List<RoundTripCase<*>> =
    EvidenceClass.entries.map { case("EvidenceClass $it", EvidenceClass.serializer(), it) } +
        Certainty.entries.map { case("Certainty $it", Certainty.serializer(), it) } +
        BuildSystem.entries.map { case("BuildSystem $it", BuildSystem.serializer(), it) } +
        ModuleTrait.entries.map { case("ModuleTrait $it", ModuleTrait.serializer(), it) } +
        Platform.entries.map { case("Platform $it", Platform.serializer(), it) } +
        SourceSetRole.entries.map { case("SourceSetRole $it", SourceSetRole.serializer(), it) } +
        ArtifactCategory.entries.map { case("ArtifactCategory $it", ArtifactCategory.serializer(), it) } +
        SymbolCategory.entries.map { case("SymbolCategory $it", SymbolCategory.serializer(), it) } +
        Visibility.entries.map { case("Visibility $it", Visibility.serializer(), it) } +
        SymbolFlag.entries.map { case("SymbolFlag $it", SymbolFlag.serializer(), it) } +
        RelationKind.entries.map { case("RelationKind $it", RelationKind.serializer(), it) } +
        OwnershipKind.entries.map { case("OwnershipKind $it", OwnershipKind.serializer(), it) } +
        DiagnosticSeverity.entries.map { case("DiagnosticSeverity $it", DiagnosticSeverity.serializer(), it) }

private val sealedCases: List<RoundTripCase<*>> =
    Samples.nodeRefs.map { case("NodeRef ${it::class.simpleName}", NodeRef.serializer(), it) } +
        Samples.resolutions.map { case("Resolution ${it::class.simpleName}", Resolution.serializer(), it) } +
        Samples.origins.map { case("SymbolOrigin ${it::class.simpleName}", SymbolOrigin.serializer(), it) } +
        Samples.facts.map { case("Fact ${it::class.simpleName}", Fact.serializer(), it) }

private val structureCases: List<RoundTripCase<*>> = listOf(
    case("Repository", Repository.serializer(), Samples.repository),
    case("BuildRoot", BuildRoot.serializer(), Samples.buildRoot),
    case("Module", Module.serializer(), Samples.module),
    case("Target", Target.serializer(), Samples.target),
    case("Variant", Variant.serializer(), Samples.variant),
    case("SourceRoot", SourceRoot.serializer(), Samples.sourceSet.roots.first()),
    case("SourceSet", SourceSet.serializer(), Samples.sourceSet),
    case("ArtifactType", ArtifactType.serializer(), Samples.artifact.type),
    case("GeneratedInfo", GeneratedInfo.serializer(), GeneratedInfo("aidl", null)),
    case("Artifact", Artifact.serializer(), Samples.artifact),
    case("Artifact without generator", Artifact.serializer(), Samples.artifact.copy(generated = null)),
)

private val contentCases: List<RoundTripCase<*>> = listOf(
    case("SymbolKind", SymbolKind.serializer(), Samples.symbol.kind),
    case("Symbol", Symbol.serializer(), Samples.symbol),
    case("Symbol without optional parts", Symbol.serializer(), bareSymbol()),
    case("Relationship", Relationship.serializer(), Samples.relationship),
    case("SymbolicReference", SymbolicReference.serializer(), Samples.symbolicReference),
    case(
        "SymbolicReference without site",
        SymbolicReference.serializer(),
        Samples.symbolicReference.copy(subKind = null, site = null, scope = Scope()),
    ),
    case("Metric", Metric.serializer(), Samples.metric),
    case("Metric with a fraction", Metric.serializer(), Samples.metric.copy(value = 0.3333333333333333)),
    case("Metric with a large value", Metric.serializer(), Samples.metric.copy(value = 1.0E22)),
    case("Metric with a small value", Metric.serializer(), Samples.metric.copy(value = 4.9E-324)),
    case("Diagnostic", Diagnostic.serializer(), Samples.diagnostic),
    case("ProducerRef", ProducerRef.serializer(), Samples.producer),
    case("OwnershipUnit", OwnershipUnit.serializer(), Samples.owner),
    case("FactBatch", FactBatch.serializer(), Samples.batch),
    case("FactBatch list", ListSerializer(FactBatch.serializer()), listOf(Samples.batch, Samples.batch)),
)

internal val roundTripCases: List<RoundTripCase<*>> =
    identifierCases + enumCases + sealedCases + structureCases + contentCases

private fun bareSymbol(): Symbol = Samples.symbol.copy(
    qualifiedName = null,
    signature = null,
    visibility = null,
    flags = emptySet(),
    declaration = null,
    nameLocation = null,
    documentation = null,
    scope = Scope(),
    attributes = emptyMap(),
)
